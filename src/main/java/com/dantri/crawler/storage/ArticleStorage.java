package com.dantri.crawler.storage;

import com.dantri.crawler.domain.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Service
public class ArticleStorage {
    private static final Logger log = LoggerFactory.getLogger(ArticleStorage.class);
    private static final SimpleDateFormat YEAR_FMT  = new SimpleDateFormat("yyyy");
    private static final SimpleDateFormat MONTH_FMT = new SimpleDateFormat("MM");
    private static final SimpleDateFormat TS_FMT    = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
    private static final String BASE_DIR = "data";

    public void save(Article a) {
        try {
            Date p = a.getPublishTime();
            if (p == null) {
                log.warn("No publishTime, skip save: {}", a.getUrl());
                return;
            }
            String domain = new URI(a.getUrl()).getHost();
            String year   = YEAR_FMT.format(p);
            String month  = MONTH_FMT.format(p);
            String dir    = BASE_DIR + File.separator + domain + File.separator + year + File.separator + month;
            Files.createDirectories(new File(dir).toPath());

            String ts    = TS_FMT.format(p);
            String rand  = UUID.randomUUID().toString().substring(0, 6);
            String name  = ts + "_" + rand + ".csv";
            File   file  = new File(dir, name);

            try (FileWriter w = new FileWriter(file)) {
                w.write("url,title,description,content,publishTime,author,category\n");
                w.append(csv(a.getUrl())).append(',')
                        .append(csv(a.getTitle())).append(',')
                        .append(csv(a.getDescription())).append(',')
                        .append(csv(a.getContent())).append(',')
                        .append(p.toString()).append(',')
                        .append(csv(a.getAuthor())).append(',')
                        .append(csv(a.getCategory())).append('\n');
            }
            log.info("Saved article: {} via {}", a.getUrl(), a.getParseLayer());
        } catch (Exception e) {
            log.error("Error saving article {}", a.getUrl(), e);
        }
    }

    private String csv(String s) {
        return s == null ? "" : s.replaceAll("[,\n\r]", " ");
    }
}
