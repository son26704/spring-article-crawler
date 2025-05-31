package com.dantri.crawler.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.dantri.crawler.domain.Article;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArticleStorage {
    private static final Logger log = LoggerFactory.getLogger(ArticleStorage.class);
    private static final SimpleDateFormat ISO_DATE_FMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ElasticsearchClient client;

    public void save(Article a) {
        try {
            if (a.getPublishTime() == null) {
                log.warn("No publishTime, skip save: {}", a.getUrl());
                return;
            }
            Map<String, Object> article = Map.of(
                    "url", a.getUrl() != null ? a.getUrl() : "",
                    "title", a.getTitle() != null ? a.getTitle() : "",
                    "description", a.getDescription() != null ? a.getDescription() : "",
                    "content", a.getContent() != null ? a.getContent() : "",
                    "published_date", ISO_DATE_FMT.format(a.getPublishTime()),
                    "author", a.getAuthor() != null ? a.getAuthor() : "",
                    "category", a.getCategory() != null ? a.getCategory() : "",
                    "source", new java.net.URI(a.getUrl()).getHost(),
                    "parse_layer", a.getParseLayer() != null ? a.getParseLayer() : ""
            );
            client.index(i -> i
                    .index("articles")
                    .id(a.getUrl())
                    .document(article)
            );
            log.info("Saved article to Elasticsearch: {} via {}", a.getUrl(), a.getParseLayer());
        } catch (Exception e) {
            log.error("Error saving article to Elasticsearch: {}", a.getUrl(), e);
        }
    }
}
