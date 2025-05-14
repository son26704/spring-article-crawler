package com.dantri.crawler.visited;

import java.util.List;

public interface VisitedUrlStore {
    boolean isVisited(String url);
    void markVisited(String url);
    long count();
    List<String> listAll();
    void clearAll();
}
