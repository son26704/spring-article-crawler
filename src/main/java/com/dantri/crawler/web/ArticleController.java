package com.dantri.crawler.web;

import com.dantri.crawler.service.ArticleService;
import com.dantri.crawler.service.ArticleService.ArticleSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/{domain}")
    public ResponseEntity<List<ArticleSummary>> byDomain(
            @PathVariable String domain,
            @RequestParam(defaultValue = "100") int limit) throws IOException {

        if (limit <= 0) limit = 100;
        return ResponseEntity.ok(articleService.listByDomain(domain, limit));
    }

    @GetMapping("/{domain}/{year}/{month}")
    public ResponseEntity<List<ArticleSummary>> byFolder(
            @PathVariable String domain,
            @PathVariable String year,
            @PathVariable String month) throws IOException {

        List<ArticleSummary> list = articleService.listInFolder(domain, year, month);
        return ResponseEntity.ok(list);
    }
}
