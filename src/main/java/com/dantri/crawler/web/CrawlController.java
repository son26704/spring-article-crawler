package com.dantri.crawler.web;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.Time;
import com.dantri.crawler.domain.Article;
import com.dantri.crawler.queue.CrawlQueueManager;
import com.dantri.crawler.queue.UrlTask;
import com.dantri.crawler.visited.NonArticleUrlStore;
import com.dantri.crawler.visited.VisitedUrlStore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class CrawlController {

    @Qualifier("esVisited")
    private final VisitedUrlStore visitedMgr;
    @Qualifier("redisNonArticle")
    private final NonArticleUrlStore nonArticleStore;
    @Qualifier("redisQueue")
    private final CrawlQueueManager queueManager;
    private final ElasticsearchClient client;

    private static final SimpleDateFormat ISO_DATE_FMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    @GetMapping("/queue/size")
    public Map<String, Integer> getQueueSize() {
        int size = queueManager.size();
        return Map.of("queueSize", size);
    }

    @PostMapping("/queue")
    public ResponseEntity<EnqueueResponse> enqueueUrl(@RequestBody EnqueueRequest req) {
        queueManager.pushTask(new UrlTask(req.getUrl(), 0));
        log.info("Enqueued via API: {}", req.getUrl());
        return ResponseEntity.ok(new EnqueueResponse("ok", req.getUrl()));
    }

    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        long visitedCount = visitedMgr.count();
        long nonArticleCount = nonArticleStore.count();
        return Map.of(
                "visitedUrls", visitedCount,
                "nonArticleUrls", nonArticleCount
        );
    }

    @GetMapping("/visited")
    public List<String> listVisited() {
        return visitedMgr.listAll();
    }

    @DeleteMapping("/visited")
    public void clearVisited() throws IOException {
        visitedMgr.clearAll();
    }

    @GetMapping("/non-article")
    public List<String> listNonArticle() {
        return nonArticleStore.listAll();
    }

    @DeleteMapping("/non-article")
    public void clearNonArticle() throws IOException {
        nonArticleStore.clearAll();
    }

    @GetMapping("/search")
    public SearchResult search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws IOException {

        final int finalSize = size > 100 ? 100 : size;
        final int from = page * finalSize;

        SearchResponse<Map> response = client.search(s -> s
                        .index("articles")
                        .query(q -> q.multiMatch(m -> m
                                .fields("title", "content")
                                .query(keyword)
                        ))
                        .from(from)
                        .size(finalSize),
                Map.class
        );

        List<Article> articles = response.hits().hits().stream()
                .map(hit -> {
                    Map<String, Object> source = hit.source();
                    Article article = new Article();
                    article.setUrl((String) source.get("url"));
                    article.setTitle((String) source.get("title"));
                    article.setDescription((String) source.get("description"));
                    article.setContent((String) source.get("content"));
                    article.setAuthor((String) source.get("author"));
                    article.setCategory((String) source.get("category"));
                    article.setParseLayer((String) source.get("parse_layer"));
                    try {
                        String publishedDate = (String) source.get("published_date");
                        if (publishedDate != null) {
                            article.setPublishTime(ISO_DATE_FMT.parse(publishedDate));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse published_date for article: {}", source.get("url"));
                    }
                    return article;
                })
                .collect(Collectors.toList());

        long totalHits = response.hits().total().value();
        int totalPages = (int) Math.ceil((double) totalHits / finalSize);

        return new SearchResult(
                articles,
                totalHits,
                page,
                finalSize,
                totalPages,
                page < totalPages - 1,
                page > 0
        );
    }

    @GetMapping("/search/export")
    public List<Article> exportSearch(@RequestParam String keyword,
                                      @RequestParam(defaultValue = "1000") int limit) throws IOException {
        List<Article> results = new ArrayList<>();
        final int batchSize = 500;

        // Giới hạn limit tối đa
        final int finalLimit = limit > 10000 ? 10000 : limit;

        SearchResponse<Map> initialResponse = client.search(s -> s
                        .index("articles")
                        .query(q -> q.multiMatch(m -> m
                                .fields("title", "content")
                                .query(keyword)
                        ))
                        .size(batchSize)
                        .scroll(Time.of(t -> t.time("1m"))),
                Map.class
        );

        String currentScrollId = initialResponse.scrollId();
        results.addAll(initialResponse.hits().hits().stream()
                .map(this::mapToArticle)
                .collect(Collectors.toList()));
        int totalFetched = initialResponse.hits().hits().size();

        while (totalFetched < finalLimit && currentScrollId != null) {
            final String finalScrollId = currentScrollId;
            ScrollRequest scrollRequest = ScrollRequest.of(s -> s
                    .scrollId(finalScrollId)
                    .scroll(Time.of(t -> t.time("1m")))
            );
            ScrollResponse<Map> scrollResponse = client.scroll(scrollRequest, Map.class);

            currentScrollId = scrollResponse.scrollId();
            if (scrollResponse.hits().hits().isEmpty()) {
                break;
            }

            List<Article> batchResults = scrollResponse.hits().hits().stream()
                    .map(this::mapToArticle)
                    .collect(Collectors.toList());
            results.addAll(batchResults);
            totalFetched += batchResults.size();
        }

        // Clear scroll context khi hoàn tất
        if (currentScrollId != null) {
            final String finalScrollIdForClear = currentScrollId;
            client.clearScroll(c -> c.scrollId(finalScrollIdForClear));
        }

        return results.subList(0, Math.min(finalLimit, results.size()));
    }

    private Article mapToArticle(co.elastic.clients.elasticsearch.core.search.Hit<Map> hit) {
        Map<String, Object> source = hit.source();
        Article article = new Article();
        article.setUrl((String) source.get("url"));
        article.setTitle((String) source.get("title"));
        article.setDescription((String) source.get("description"));
        article.setContent((String) source.get("content"));
        article.setAuthor((String) source.get("author"));
        article.setCategory((String) source.get("category"));
        article.setParseLayer((String) source.get("parse_layer"));
        try {
            String publishedDate = (String) source.get("published_date");
            if (publishedDate != null) {
                article.setPublishTime(ISO_DATE_FMT.parse(publishedDate));
            }
        } catch (Exception e) {
            log.warn("Failed to parse published_date for article: {}", source.get("url"));
        }
        return article;
    }

    @Data
    static class EnqueueRequest {
        private String url;
    }

    @Data
    static class EnqueueResponse {
        private final String status;
        private final String url;
    }

    @Data
    @AllArgsConstructor
    static class SearchResult {
        private List<Article> articles;
        private long totalHits;
        private int currentPage;
        private int pageSize;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;
    }
}