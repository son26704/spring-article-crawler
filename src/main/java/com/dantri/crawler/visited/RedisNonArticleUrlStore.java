package com.dantri.crawler.visited;

import com.dantri.crawler.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Qualifier("redisNonArticle")
@RequiredArgsConstructor
public class RedisNonArticleUrlStore implements NonArticleUrlStore {

    private final StringRedisTemplate redis;
    private final CrawlerProperties props;

    private long ttl() {
        return props.getSettings().getNonArticleTtl();
    }

    private String key(String url) {
        return props.getRedisPrefix() + ":non:" + url;
    }

    @Override
    public boolean isNonArticle(String url) {
        return redis.hasKey(key(url));
    }

    @Override
    public void markNonArticle(String url) {
        redis.opsForValue().set(key(url), "1", ttl(), TimeUnit.MILLISECONDS);
    }

    @Override
    public long count() {
        long count = 0;
        ScanOptions options = ScanOptions.scanOptions().match(props.getRedisPrefix() + ":non:*").count(1000).build();
        try (Cursor<String> cursor = redis.opsForValue().getOperations().scan(options)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> listAll() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(props.getRedisPrefix() + ":non:*").count(1000).build();
        try (Cursor<String> cursor = redis.opsForValue().getOperations().scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    @Override
    public void clearAll() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(props.getRedisPrefix() + ":non:*")
                .count(1000)
                .build();
        try (var cursor = redis.opsForValue().getOperations().scan(options)) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        }
    }
}
