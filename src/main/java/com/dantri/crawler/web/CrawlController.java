package com.dantri.crawler.web;

import com.dantri.crawler.queue.CrawlQueueManager;
import com.dantri.crawler.queue.UrlTask;
//import com.dantri.crawler.visited.NonArticleStore;
import com.dantri.crawler.visited.NonArticleUrlStore;
import com.dantri.crawler.visited.VisitedUrlStore;
//import com.dantri.crawler.visited.VisitedUrlsManager;
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

    @Qualifier("redisVisited")
    private final VisitedUrlStore visitedMgr;
    @Qualifier("redisNonArticle")
    private final NonArticleUrlStore nonArticleStore;
    @Qualifier("redisQueue")
    private final CrawlQueueManager queueManager;

//    private final VisitedUrlsManager visitedMgr;
//    private final NonArticleStore nonArticleStore;

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
        long visitedCount    = visitedMgr.count();
        long nonArticleCount = nonArticleStore.count();
        return Map.of(
                "visitedUrls",    visitedCount,
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

    @Data static class EnqueueRequest {
        private String url;
    }
    @Data static class EnqueueResponse {
        private final String status;
        private final String url;
    }
}
