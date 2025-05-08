package com.dantri.crawler.worker;

import com.dantri.crawler.config.CrawlerProperties;
import com.dantri.crawler.domain.Article;
import com.dantri.crawler.parser.UniversalArticleParser;
import com.dantri.crawler.queue.CrawlQueueManager;
import com.dantri.crawler.queue.UrlTask;
import com.dantri.crawler.storage.ArticleStorage;
import com.dantri.crawler.visited.NonArticleStore;
import com.dantri.crawler.visited.VisitedUrlsManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class CrawlWorker implements Runnable {

    private final CrawlQueueManager      queue;
    private final VisitedUrlsManager     visited;
    private final NonArticleStore        nonArticleStore;
    private final UniversalArticleParser parser;
    private final ArticleStorage         storage;

    private int maxLevel;
    private AtomicBoolean running;
    private CrawlerProperties props;

    private Set<String> seedSet;

    private static final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public CrawlWorker init(int maxLevel,
                            AtomicBoolean running,
                            CrawlerProperties props) {
        this.maxLevel = maxLevel;
        this.running = running;
        this.props = props;
        this.seedSet = props.getStartUrls().stream()
                .map(this::normalize)
                .collect(Collectors.toSet());
        return this;
    }

    @Override
    public void run() {
        log.info("Worker {} started", Thread.currentThread().getName());
        long sixMonthsMs = props.getSettings().getSixMonthsMillis();

        while (running.get()) {
            UrlTask task = queue.takeTask();
            if (task == null) continue;

            String rawUrl = task.getUrl();
            String url = normalize(rawUrl);
            int level = task.getLevel();

            if (seedSet.contains(url)) {
                if (level < maxLevel) {
                    extractAndQueueLinks(url, level + 1);
                }
                inProgress.remove(url);
                continue;
            }

            if (visited.isVisited(url) || nonArticleStore.isNonArticle(url)) {
                inProgress.remove(url);
                continue;
            }
            if (!inProgress.add(url)) {
                continue;
            }

            try {
                Article art = parser.parseUrl(url);

                if (art != null && art.getPublishTime() != null) {
                    long age = System.currentTimeMillis() - art.getPublishTime().getTime();
                    if (age <= sixMonthsMs) {
                        storage.save(art);
                    }
                    visited.markVisited(url);
                } else {
                    nonArticleStore.markNonArticle(url);
                }

                if (level < maxLevel) {
                    extractAndQueueLinks(url, level + 1);
                }
            } finally {
                inProgress.remove(url);
            }
        }
    }

    private void extractAndQueueLinks(String pageUrl, int nextLevel) {
        try {
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(8000)
                    .get();
            String baseDomain = new URI(pageUrl).getHost();

            doc.select("a[href]").forEach(e -> {
                String hrefRaw = e.absUrl("href").split("#")[0];
                if (hrefRaw.isBlank()) return;
                String href = normalize(hrefRaw);
                try {
                    if (!new URI(href).getHost().equals(baseDomain)) {
                        return;
                    }
                } catch (Exception ignored) {
                    return;
                }
                if (!visited.isVisited(href)
                        && !nonArticleStore.isNonArticle(href)
                        && inProgress.add(href)) {
                    queue.pushTask(new UrlTask(href, nextLevel));
                    inProgress.remove(href);
                }
            });
        } catch (Exception ex) {
            log.debug("extractAndQueueLinks error [{}]: {}", pageUrl, ex.getMessage());
        }
    }

    private String normalize(String url) {
        if (url == null) return "";
        String u = url.trim();

        int hashPos = u.indexOf('#');
        if (hashPos >= 0) u = u.substring(0, hashPos);

        while (u.endsWith("/") && u.length() > "http://".length()) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
