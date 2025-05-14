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
        try {
            StreamOperations<String, String, String> ops = redis.opsForStream();
            try {
                redis.opsForStream().createGroup(streamKey, ReadOffset.latest(), group);
            } catch (Exception ignored) {}

            var messages = ops.read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().block(Duration.ofSeconds(5)).count(1),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            );

            if (messages == null || messages.isEmpty()) return null;

            Map<String, String> data = messages.get(0).getValue();
            String url = data.get("url");
            int level  = Integer.parseInt(data.get("level"));
            return new UrlTask(url, level);
        } catch (Exception e) {
            log.warn("XREADGROUP failed", e);
            return null;
        }
    }

    @Override
    public int size() {
        try {
            return redis.opsForStream().size(streamKey).intValue();
        } catch (Exception e) {
            return -1;
        }
    }
}
