package com.dantri.crawler.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Primary
@Component
@Qualifier("kafkaQueue")
@RequiredArgsConstructor
public class KafkaQueueManager implements CrawlQueueManager {

    private final KafkaTemplate<String, UrlTask> kafkaTemplate;
    private final BlockingQueue<UrlTask> queue = new LinkedBlockingQueue<>(100000);
    private static final String TOPIC = "crawler-tasks";

    @Override
    public void pushTask(UrlTask t) {
        try {
            kafkaTemplate.send(TOPIC, t.getUrl(), t);
        } catch (Exception e) {
            log.warn("Failed to send message to Kafka topic {}: {}", TOPIC, t.getUrl(), e);
        }
    }

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void listen(UrlTask task) {
        try {
            boolean added = queue.offer(task, 5, TimeUnit.SECONDS);
            if (!added) {
                log.warn("Queue is full, failed to add task: {}", task.getUrl());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to add task to queue", e);
        }
    }

    @Override
    public UrlTask takeTask() {
        try {
            return queue.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public long getPendingMessagesCount() {
        // This is complex to get accurately with Kafka without using specific APIs
        // like KafkaConsumer.partitionsFor() and get end offsets.
        // Returning -1 to indicate not supported/unknown for now.
        return -1;
    }

    @Override
    public void cleanupOldConsumers(long idleThresholdMs) {
        // This is a Redis Stream specific concept and not applicable to Kafka consumers.
        log.info("cleanupOldConsumers is not applicable for KafkaQueueManager");
    }
} 