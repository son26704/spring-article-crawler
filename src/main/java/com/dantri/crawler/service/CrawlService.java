package com.dantri.crawler.service;

import com.dantri.crawler.config.CrawlerProperties;
import com.dantri.crawler.job.CrawlJob;
import com.dantri.crawler.queue.CrawlQueueManager;
import com.dantri.crawler.worker.CrawlWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlService implements ApplicationListener<ApplicationReadyEvent> {

    private final CrawlQueueManager       queueManager;
    private final CrawlerProperties       props;
    private final SchedulerFactoryBean    schedulerFactory;
    private final ObjectProvider<CrawlWorker> workerProvider;

    private final AtomicBoolean running = new AtomicBoolean(true);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        startWorkers();
        scheduleQuartz();
    }

    private void startWorkers() {
        int threads = props.getSettings().getThreadPoolSize();
        int maxLevel = props.getSettings().getDefaultMaxLevel();

        for (int i = 0; i < threads; i++) {
            CrawlWorker worker = workerProvider.getObject()
                    .init(maxLevel, running, props);
            Thread t = new Thread(worker, "Worker-" + i);
            t.start();
        }
        log.info("Started {} CrawlWorker threads (maxLevel={})", threads, maxLevel);
    }

    private void scheduleQuartz() {
        try {
            Scheduler scheduler = schedulerFactory.getScheduler();

            JobDetail jobDetail = JobBuilder.newJob(CrawlJob.class)
                    .withIdentity("crawlJob")
                    .storeDurably()
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity("crawlTrigger")
                    .startNow()
                    .withSchedule(
                            SimpleScheduleBuilder.simpleSchedule()
                                    .withIntervalInMinutes(5)
                                    .repeatForever()
                    )
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            scheduler.start();
            log.info("Quartz scheduler started: CrawlJob every 5 minutes");
        } catch (SchedulerException e) {
            log.error("Failed to schedule CrawlJob", e);
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        try {
            schedulerFactory.getScheduler().shutdown(true);
            log.info("Quartz scheduler shut down");
        } catch (SchedulerException e) {
            log.warn("Error shutting down scheduler", e);
        }
        log.info("CrawlService stopped");
    }
}
