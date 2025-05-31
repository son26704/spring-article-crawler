package com.dantri.crawler.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchInitializer {

    private final ElasticsearchClient client;

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed(ContextRefreshedEvent event) {
        try {
            boolean exists = client.indices().exists(e -> e.index("articles")).value();
            if (!exists) {
                CreateIndexRequest request = CreateIndexRequest.of(b -> b
                        .index("articles")
                        .mappings(m -> m
                                .properties("url", p -> p.keyword(k -> k))
                                .properties("title", p -> p.text(t -> t.analyzer("standard")))
                                .properties("content", p -> p.text(t -> t.analyzer("standard")))
                                .properties("published_date", p -> p.date(d -> d))
                                .properties("tags", p -> p.keyword(k -> k))
                                .properties("source", p -> p.keyword(k -> k))
                                .properties("parse_layer", p -> p.keyword(k -> k))
                                .properties("description", p -> p.text(t -> t.analyzer("standard")))
                                .properties("author", p -> p.keyword(k -> k))
                                .properties("category", p -> p.keyword(k -> k))
                        )
                );
                client.indices().create(request);
                log.info("Created Elasticsearch index: articles");
            } else {
                log.info("Elasticsearch index 'articles' already exists");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Elasticsearch index", e);
            throw new RuntimeException(e);
        }
    }
}