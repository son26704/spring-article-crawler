package com.dantri.crawler.job;

import com.dantri.crawler.config.CrawlerProperties;
import com.dantri.crawler.queue.CrawlQueueManager;
import com.dantri.crawler.queue.RedisQueueManager;
import com.dantri.crawler.queue.UrlTask;
import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
@DisallowConcurrentExecution
public class CrawlJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(CrawlJob.class);

    @Qualifier("kafkaQueue")
    private final CrawlQueueManager queue;

    private final CrawlerProperties props;

    @Override
    public void execute(JobExecutionContext context) {
        try {
            if (queue instanceof RedisQueueManager redisQueueManager) {
                redisQueueManager.trimStream(100000);
            }
            int maxUrls = props.getSettings().getMaxUrlsPerCrawl();
            int added = 0;
            for (String url : props.getStartUrls()) {
                queue.pushTask(new UrlTask(url, 0));
                log.info("Scheduled startUrl: {}", url);
                added++;
                if (added >= maxUrls) break;
            }
            log.info("CrawlJob added {} URLs to queue", added);
        } catch (Exception e) {
            log.error("CrawlJob failed", e);
        }
    }
}