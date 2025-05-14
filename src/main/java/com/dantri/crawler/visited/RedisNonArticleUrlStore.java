package com.dantri.crawler.visited;

import com.dantri.crawler.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
        return redis.keys(props.getRedisPrefix() + ":non:*").size();
    }

    @Override
    public List<String> listAll() {
        return redis.keys(props.getRedisPrefix() + ":non:*").stream().toList();
    }

    @Override
    public void clearAll() {
        redis.delete(redis.keys(props.getRedisPrefix() + ":non:*"));
    }

}
