package com.dantri.crawler.web;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
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

    @GetMapping("/queue/pending")
    public Map<String, Long> getPendingMessagesCount() {
        return Map.of("pendingMessages", queueManager.getPendingMessagesCount());
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
    public List<Article> search(@RequestParam String keyword) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                        .index("articles")
                        .query(q -> q.multiMatch(m -> m
                                .fields("title", "content")
                                .query(keyword)
                        ))
                        .size(500),
                Map.class
        );
        return response.hits().hits().stream()
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
}