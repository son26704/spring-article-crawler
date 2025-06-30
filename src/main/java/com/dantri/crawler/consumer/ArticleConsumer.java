package com.dantri.crawler.consumer;

import com.dantri.crawler.domain.Article;
import com.dantri.crawler.service.ArticleService;
import com.dantri.crawler.storage.ArticleStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleConsumer {
    private final ArticleStorage storage;

    @KafkaListener(topics = "crawl-results", groupId = "article-indexer")
    public void consume(Article article) {
        storage.save(article);
//        log.info("Consumed and saved article: {}", article.getUrl());
    }
}
