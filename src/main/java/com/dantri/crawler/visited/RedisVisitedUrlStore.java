package com.dantri.crawler.visited;

import com.dantri.crawler.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Qualifier("redisVisited")
@RequiredArgsConstructor
public class RedisVisitedUrlStore implements VisitedUrlStore {

    private final StringRedisTemplate redis;
    private final CrawlerProperties props;

    private String redisKey() {
        return props.getRedisPrefix() + ":visited";
    }

    @Override
    public boolean isVisited(String url) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(redisKey(), url));
    }

    @Override
    public void markVisited(String url) {
        redis.opsForSet().add(redisKey(), url);
    }

    @Override
    public long count() {
        Long size = redis.opsForSet().size(redisKey());
        return size != null ? size : 0;
    }

    @Override
    public List<String> listAll() {
        return redis.opsForSet().members(redisKey()).stream().toList();
    }

    @Override
    public void clearAll() {
        redis.delete(redisKey());
    }
}
