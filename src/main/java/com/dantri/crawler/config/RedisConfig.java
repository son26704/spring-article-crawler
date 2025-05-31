package com.dantri.crawler.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

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

        ClientOptions clientOptions = ClusterClientOptions.builder()
                .autoReconnect(true)
                .timeoutOptions(io.lettuce.core.TimeoutOptions.enabled(Duration.ofSeconds(10)))
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(10))
                .clientOptions(clientOptions)
                .build();

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    @Bean
    public StringRedisTemplate redisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
