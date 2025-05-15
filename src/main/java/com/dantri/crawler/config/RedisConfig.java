package com.dantri.crawler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
        clusterConfig.clusterNode("localhost", 6379);
        clusterConfig.clusterNode("localhost", 6380);
        clusterConfig.clusterNode("localhost", 6381);
        clusterConfig.clusterNode("localhost", 6382);
        clusterConfig.clusterNode("localhost", 6383);
        clusterConfig.clusterNode("localhost", 6384);
        return new LettuceConnectionFactory(clusterConfig);
    }

    @Bean
    public StringRedisTemplate redisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
