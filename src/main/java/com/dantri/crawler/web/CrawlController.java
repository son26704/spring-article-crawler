package com.dantri.crawler.web;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import java.util.List;
import java.util.Map;

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

    @PostMapping("/test-index")
    public ResponseEntity<String> testIndex() throws IOException {
        Map<String, Object> article = Map.of(
                "url", "https://dantri.com.vn/test",
                "title", "Test Article",
                "content", "This is a test article for Elasticsearch.",
                "published_date", "2025-05-29T08:00:00Z",
                "tags", List.of("test", "news"),
                "source", "dantri.com.vn"
        );
        client.index(i -> i.index("articles").id("https://dantri.com.vn/test").document(article));
        return ResponseEntity.ok("Indexed test article");
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