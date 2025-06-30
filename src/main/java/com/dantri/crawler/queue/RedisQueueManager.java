package com.dantri.crawler.queue;

import com.dantri.crawler.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@Qualifier("redisQueue")
@RequiredArgsConstructor
public class RedisQueueManager implements CrawlQueueManager {

    private final StringRedisTemplate redis;
    private final CrawlerProperties props;

    private final String streamKey = "crawler:queue";
    private final String group = "crawler-group";
    private final String consumer = "worker-" + System.currentTimeMillis();

    @Override
    public void pushTask(UrlTask t) {
        try {
            redis.opsForStream().add(MapRecord.create(streamKey, Map.of(
                    "url", t.getUrl(),
                    "level", String.valueOf(t.getLevel())
            )));
        } catch (Exception e) {
            log.warn("XADD failed: {}", t.getUrl(), e);
        }
    }

    @Override
    public UrlTask takeTask() {
        int maxRetries = 3;
        int retryDelayMs = 1000;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                StreamOperations<String, String, String> ops = redis.opsForStream();
                try {
                    redis.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
                } catch (Exception ignored) {}
                var messages = ops.read(
                        Consumer.from(group, consumer),
                        StreamReadOptions.empty().block(Duration.ofSeconds(5)).count(1),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                );
                if (messages == null || messages.isEmpty()) return null;
                MapRecord<String, String, String> message = messages.get(0);
                Map<String, String> data = message.getValue();
                String messageId = message.getId().toString();
                String url = data.get("url");
                int level = Integer.parseInt(data.get("level"));
                ops.acknowledge(streamKey, group, messageId);
                ops.delete(streamKey, messageId);
//                log.debug("Acknowledged and deleted message ID: {}", messageId);
                return new UrlTask(url, level);
            } catch (Exception e) {
                log.warn("XREADGROUP failed for streamKey={}, group={}, consumer={}, attempt={}/{}",
                        streamKey, group, consumer, attempt, maxRetries, e);
                if (attempt == maxRetries) {
                    log.error("Max retries reached for XREADGROUP, returning null");
                    return null;
                }
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    public int size() {
        try {
            return redis.opsForStream().size(streamKey).intValue();
        } catch (Exception e) {
            return -1;
        }
    }

    public void cleanupOldConsumers(long idleThresholdMs) {
        try {
            StreamOperations<String, String, String> ops = redis.opsForStream();
            StreamInfo.XInfoConsumers consumers = ops.consumers(streamKey, group);
            for (StreamInfo.XInfoConsumer consumerInfo : consumers) {
                String consumerName = consumerInfo.consumerName();
                long idleTimeMs = consumerInfo.idleTimeMs();
                if (idleTimeMs > idleThresholdMs) {
                    ops.deleteConsumer(streamKey, Consumer.from(group, consumerName));
                    log.info("Deleted idle consumer: {}", consumerName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup old consumers", e);
        }
    }

    public void trimStream(long maxLength) {
        try {
            redis.opsForStream().trim(streamKey, maxLength);
            log.info("Trimmed stream {} to max length {}", streamKey, maxLength);
        } catch (Exception e) {
            log.warn("Failed to trim stream {}", streamKey, e);
        }
    }
}