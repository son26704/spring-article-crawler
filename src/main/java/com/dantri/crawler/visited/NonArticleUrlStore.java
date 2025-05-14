package com.dantri.crawler.visited;

import java.util.List;

public interface NonArticleUrlStore {
    boolean isNonArticle(String url);
    void markNonArticle(String url);
    long count();
    List<String> listAll();
    void clearAll();
}
