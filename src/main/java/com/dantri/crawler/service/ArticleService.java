package com.dantri.crawler.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ElasticsearchClient client;

    // Constants
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_FOLDER_SIZE = 1000;
    private static final String ARTICLES_INDEX = "articles";
    private static final String SOURCE_FIELD = "source";
    private static final String PUBLISHED_DATE_FIELD = "published_date";
    private static final String URL_FIELD = "url";
    private static final String TITLE_FIELD = "title";
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public List<ArticleSummary> listByDomain(String domain, int limit) throws IOException {
        validateDomain(domain);
        final int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 10000);

        log.debug("Searching articles for domain: {} with limit: {}", domain, effectiveLimit);

        SearchResponse<Map> response = client.search(s -> s
                        .index(ARTICLES_INDEX)
                        .query(createTermQuery(SOURCE_FIELD, domain))
                        .size(effectiveLimit)
                        .sort(createPublishedDateSort())
                        .source(createSourceFilter()),
                Map.class
        );

        return mapToArticleSummaries(response);
    }

    public List<ArticleSummary> listInFolder(String domain, String year, String month) throws IOException {
        validateDomain(domain);
        validateYearMonth(year, month);

        log.debug("Searching articles for domain: {} in {}/{}", domain, year, month);

        // Sử dụng YearMonth để tính toán chính xác ngày cuối tháng
        YearMonth yearMonth = YearMonth.of(Integer.parseInt(year), Integer.parseInt(month));
        String fromDate = yearMonth.atDay(1).atStartOfDay().format(ISO_DATE_TIME);
        String toDate = yearMonth.atEndOfMonth().atTime(23, 59, 59).format(ISO_DATE_TIME);

        Query rangeQuery = createDateRangeQuery(fromDate, toDate);

        SearchResponse<Map> response = client.search(s -> s
                        .index(ARTICLES_INDEX)
                        .query(q -> q.bool(b -> b
                                .filter(createTermQuery(SOURCE_FIELD, domain))
                                .filter(rangeQuery)
                        ))
                        .size(MAX_FOLDER_SIZE)
                        .sort(createPublishedDateSort())
                        .source(createSourceFilter()),
                Map.class
        );

        return mapToArticleSummaries(response);
    }

    // Helper methods để tái sử dụng code
    private void validateDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            throw new IllegalArgumentException("Domain cannot be null or empty");
        }
    }

    private void validateYearMonth(String year, String month) {
        try {
            int yearInt = Integer.parseInt(year);
            int monthInt = Integer.parseInt(month);

            if (yearInt < 1900 || yearInt > LocalDate.now().getYear() + 1) {
                throw new IllegalArgumentException("Invalid year: " + year);
            }

            if (monthInt < 1 || monthInt > 12) {
                throw new IllegalArgumentException("Invalid month: " + month);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Year and month must be valid numbers", e);
        }
    }

    private Query createTermQuery(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private Query createDateRangeQuery(String fromDate, String toDate) {
        String rangeQueryJson = String.format("""
            {
                "range": {
                    "%s": {
                        "gte": "%s",
                        "lte": "%s"
                    }
                }
            }
            """, PUBLISHED_DATE_FIELD, fromDate, toDate);
        return Query.of(q -> q.withJson(new StringReader(rangeQueryJson)));
    }

    private co.elastic.clients.elasticsearch.core.search.SourceConfig createSourceFilter() {
        return co.elastic.clients.elasticsearch.core.search.SourceConfig.of(s -> s
                .filter(f -> f.includes(URL_FIELD, TITLE_FIELD))
        );
    }

    private co.elastic.clients.elasticsearch._types.SortOptions createPublishedDateSort() {
        return co.elastic.clients.elasticsearch._types.SortOptions.of(s -> s
                .field(f -> f.field(PUBLISHED_DATE_FIELD).order(SortOrder.Desc))
        );
    }

    private List<ArticleSummary> mapToArticleSummaries(SearchResponse<Map> response) {
        return response.hits().hits().stream()
                .map(hit -> {
                    Map<String, Object> source = hit.source();
                    return new ArticleSummary(
                            (String) source.get(URL_FIELD),
                            (String) source.get(TITLE_FIELD)
                    );
                })
                .collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    public static class ArticleSummary {
        private String url;
        private String title;
    }
}