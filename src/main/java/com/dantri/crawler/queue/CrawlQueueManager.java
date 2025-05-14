package com.dantri.crawler.queue;

public interface CrawlQueueManager {
    void pushTask(UrlTask t);
    UrlTask takeTask();
    int size();
}
