package com.dantri.crawler.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;


@Service
public class ArticleService {

    private static final Path BASE = Paths.get("data");

    public List<ArticleSummary> listByDomain(String domain, int limit) throws IOException {
        Path root = BASE.resolve(domain);
        if (!Files.isDirectory(root)) return List.of();

        List<ArticleSummary> out = new ArrayList<>();

        try (Stream<Path> years = Files.list(root).sorted(Comparator.reverseOrder())) {
            for (Path y : years.toList()) {
                try (Stream<Path> months = Files.list(y).sorted(Comparator.reverseOrder())) {
                    for (Path m : months.toList()) {
                        out.addAll(listInFolder(domain, y.getFileName().toString(),
                                m.getFileName().toString()));
                        if (out.size() >= limit) {
                            return out.subList(0, limit);
                        }
                    }
                }
            }
        }
        return out;
    }

    public List<ArticleSummary> listInFolder(String domain, String year, String month) throws IOException {
        Path folder = BASE.resolve(domain).resolve(year).resolve(month);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }

        List<ArticleSummary> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder, "*.csv")) {
            for (Path f : ds) {
                try (BufferedReader br = Files.newBufferedReader(f)) {
                    br.readLine();
                    String line = br.readLine();
                    if (line != null) {
                        String[] cols = line.split(",", 3);
                        out.add(new ArticleSummary(cols[0], cols[1], f.getFileName().toString()));
                    }
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    @Data @AllArgsConstructor
    public static class ArticleSummary {
        private String url;
        private String title;
        private String file;
    }
}
