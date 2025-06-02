package com.dantri.crawler.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ElasticsearchClient client;

    public List<ArticleSummary> listByDomain(String domain, int limit) throws IOException {
        final int effectiveLimit = limit <= 0 ? 100 : limit;
        SearchResponse<Map> response = client.search(s -> s
                        .index("articles")
                        .query(q -> q.term(t -> t.field("source").value(domain)))
                        .size(effectiveLimit)
                        .sort(s1 -> s1.field(f -> f.field("published_date").order(SortOrder.Desc)))
                        .source(s2 -> s2.filter(f -> f.includes("url", "title"))),
                Map.class
        );
        return response.hits().hits().stream()
                .map(hit -> new ArticleSummary(
                        (String) hit.source().get("url"),
                        (String) hit.source().get("title")
                ))
                .collect(Collectors.toList());
    }

    public List<ArticleSummary> listInFolder(String domain, String year, String month) throws IOException {
        String fromDate = String.format("%s-%s-01T00:00:00Z", year, month);
        String toDate = String.format("%s-%s-31T23:59:59Z", year, month);

        // Sử dụng JSON thô (của bạn)
        String rangeQueryJson = String.format("""
            {
                "range": {
                    "published_date": {
                        "gte": "%s",
                        "lte": "%s"
                    }
                }
            }
            """, fromDate, toDate);
        Query rangeQuery = Query.of(q -> q.withJson(new StringReader(rangeQueryJson)));

        SearchResponse<Map> response = client.search(s -> s
                        .index("articles")
                        .query(q -> q.bool(b -> b
                                .filter(f -> f.term(t -> t.field("source").value(domain)))
                                .filter(rangeQuery)
                        ))
                        .size(1000)
                        .sort(s1 -> s1.field(f -> f.field("published_date").order(SortOrder.Desc)))
                        .source(s2 -> s2.filter(f -> f.includes("url", "title"))),
                Map.class
        );
        return response.hits().hits().stream()
                .map(hit -> new ArticleSummary(
                        (String) hit.source().get("url"),
                        (String) hit.source().get("title")
                ))
                .collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    public static class ArticleSummary {
        private String url;
        private String title;
    }
}