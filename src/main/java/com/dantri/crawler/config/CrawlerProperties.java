package com.dantri.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {
    private String redisPrefix = "crawler";
    private List<String> startUrls = new ArrayList<>();
    private Settings settings = new Settings();

    @Data
    public static class Settings {
        private int  maxUrlsPerCrawl     = 100;
        private int  defaultMaxLevel     = 2;
        private long requestDelayMs      = 200;
        private int  threadPoolSize      = 4;
        private long sixMonthsMillis     = 15552000000L;
        private int  minBodyLength       = 150;
        private int  minTagLength        = 20;
        private long nonArticleTtl       = 18000000L;
    }
}
