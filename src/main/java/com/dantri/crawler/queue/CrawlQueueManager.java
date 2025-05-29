package com.dantri.crawler.queue;

public interface CrawlQueueManager {
    void pushTask(UrlTask t);
    UrlTask takeTask();
    int size();
    long getPendingMessagesCount();
    void cleanupOldConsumers(long idleThresholdMs);
}
