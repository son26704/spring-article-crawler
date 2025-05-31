package com.dantri.crawler.visited;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Primary
@Qualifier("esVisited")
@RequiredArgsConstructor
public class ElasticsearchVisitedUrlStore implements VisitedUrlStore {

    private final ElasticsearchClient client;

    @Override
    public boolean isVisited(String url) {
        try {
            return client.exists(e -> e.index("articles").id(url)).value();
        } catch (IOException e) {
            throw new RuntimeException("Failed to check if URL is visited: " + url, e);
        }
    }

    @Override
    public void markVisited(String url) {}

    @Override
    public long count() {
        try {
            return client.count(c -> c.index("articles")).count();
        } catch (IOException e) {
            throw new RuntimeException("Failed to count visited URLs", e);
        }
    }

    @Override
    public List<String> listAll() {
        try {
            SearchResponse<Map> response = client.search(s -> s
                            .index("articles")
                            .query(q -> q.matchAll(m -> m))
                            .size(10000)
                            .source(s1 -> s1.filter(f -> f.includes("url"))),
                    Map.class
            );
            List<String> urls = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                if (hit.source() != null) {
                    urls.add((String) hit.source().get("url"));
                }
            }
            return urls;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list visited URLs", e);
        }
    }

    @Override
    public void clearAll() {
        try {
            client.deleteByQuery(d -> d
                    .index("articles")
                    .query(q -> q.matchAll(m -> m))
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear visited URLs", e);
        }
    }
}