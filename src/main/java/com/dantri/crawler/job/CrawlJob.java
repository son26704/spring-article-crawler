package com.dantri.crawler.job;

import com.dantri.crawler.config.CrawlerProperties;
import com.dantri.crawler.queue.CrawlQueueManager;
import com.dantri.crawler.queue.UrlTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class CrawlJob implements Job {

    @Qualifier("redisQueue")
    private final CrawlQueueManager queue;

    private final CrawlerProperties props;

    @Override
    public void execute(JobExecutionContext context) {
        props.getStartUrls().forEach(url -> {
            queue.pushTask(new UrlTask(url, 0));
            log.info("Scheduled startUrl: {}", url);
        });
    }
}
