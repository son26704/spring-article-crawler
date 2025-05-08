package com.dantri.crawler.queue;

import com.dantri.crawler.config.CrawlerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class CrawlQueueManager {
    private static final Logger log = LoggerFactory.getLogger(CrawlQueueManager.class);
    private final BlockingQueue<UrlTask> queue;

    public CrawlQueueManager(CrawlerProperties props) {
        int base     = props.getSettings().getMaxUrlsPerCrawl() * 200;
        int capacity = Math.max(base, 50_000);
        this.queue   = new LinkedBlockingQueue<>(capacity);
        log.info("Queue capacity = {}", capacity);
    }

    public void pushTask(UrlTask t) {
        if (!queue.offer(t)) {
            if (log.isDebugEnabled()) log.debug("Drop URL, queue full: {}", t.getUrl());
        }
    }

    public UrlTask takeTask() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public int size() {
        return queue.size();
    }
}
