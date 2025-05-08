package com.dantri.crawler.visited;

import com.dantri.crawler.config.CrawlerProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitedUrlsManager {

    private static final Path FILE = Path.of("data/visited_urls.txt");

    private final CrawlerProperties props;
    private final Set<String> visited = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void load() {
        if (!Files.exists(FILE)) return;
        try (BufferedReader br = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) visited.add(line.trim());
            log.info("Loaded {} visited article URLs", visited.size());
        } catch (IOException e) {
            log.warn("Failed to load visited_urls.txt", e);
        }
    }

    public boolean isVisited(String url) {
        return visited.contains(url);
    }

    public synchronized void markVisited(String url) {
        if (props.getStartUrls().contains(url)) return;
        if (!visited.add(url)) return;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, url + "\n",
                    StandardCharsets.UTF_8,
                    Files.exists(FILE)
                            ? java.nio.file.StandardOpenOption.APPEND
                            : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException e) {
            log.warn("Failed to append visited URL {}", url, e);
        }
    }

    public long count() {
        return visited.size();
    }

    public List<String> listAll() {
        return new ArrayList<>(visited);
    }

    public synchronized void clearAll() throws IOException {
        visited.clear();
        Files.deleteIfExists(FILE);
    }
}
