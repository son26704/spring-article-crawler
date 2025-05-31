package com.dantri.crawler.parser;

import com.dantri.crawler.config.CrawlerProperties;
import com.dantri.crawler.domain.Article;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class UniversalArticleParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CrawlerProperties props;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ssXXX").toFormatter(Locale.US),
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS][.SSS]XXX").toFormatter(Locale.US),
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS][.SSS]").toFormatter(Locale.US),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy", new Locale("vi", "VN"))
    };
    private static final Pattern DATE_TEXT_PATTERN = Pattern.compile(
            "(?:Published|publishdate|datePublished|datePosted)[:\\s]+" +
                    "(\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}|\\w+\\s+\\d{1,2},\\s*\\d{4})",
            Pattern.CASE_INSENSITIVE
    );

    private int minBodyLength() { return props.getSettings().getMinBodyLength(); }
    private int minTagLength() { return props.getSettings().getMinTagLength(); }

    private String normalizeOffset(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("\\s+(?=[+-]\\d{2}:?\\d{2})", "");
        s = s.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");
        return s;
    }

    private OffsetDateTime tryParseOffset(String raw) {
        raw = normalizeOffset(raw);
        if (raw == null || raw.isBlank()) return null;

        for (var fmt : DATE_FORMATTERS) {
            try {
                return OffsetDateTime.parse(raw, fmt);
            } catch (DateTimeParseException ignore) {
                try {
                    return java.time.LocalDateTime.parse(raw, fmt)
                            .atZone(VN_ZONE)
                            .toOffsetDateTime();
                } catch (DateTimeParseException ignored2) {}
            }
        }
        return null;
    }


    public Article parseUrl(String url) {
        try {
            Thread.sleep(props.getSettings().getRequestDelayMs());
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .referrer("https://www.google.com")
                    .timeout(10000)
                    .get();

            // Content layer
            Article art = parseJsonLdContent(doc, url);
//            if (art == null) art = parseOgContent(doc, url);
            if (art == null) art = parseMetaContent(doc, url);
            if (art == null) art = parseBoilerpipeContent(doc, url);
            if (art == null) return null;

            // Lấy ra publishTime
            Date pub = extractDateFromJsonLd(doc);
            if (pub == null) pub = extractDateFromTimeTag(doc);
//            if (pub == null) pub = extractDateFromOg(doc);
            if (pub == null) pub = extractDateFromMeta(doc);
            if (pub == null) pub = extractDateFromText(doc);
            art.setPublishTime(pub);

            // Author
            String author = extractAuthorFromJsonLd(doc);
            if (author == null) author = extractAuthorFromMeta(doc);
            if (author == null) author = extractAuthorFromText(doc);
            art.setAuthor(author);

            // 4) Fallback tags for content
            if (art.getContent() == null || art.getContent().length() < minBodyLength()) {
                String fb = extractByTags(doc);
                if (fb.length() >= minTagLength()) {
                    art.setContent(fb);
                    art.setParseLayer(art.getParseLayer() + "+Tags");
                }
            }

            // 5) Category
            art.setCategory(extractCategory(doc, url));
            return art;

        } catch (Exception e) {
            log.debug("parseUrl error [{}]: {}", url, e.getMessage());
            return null;
        }
    }

    private Article parseJsonLdContent(Document doc, String url) {
        for (Element s : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = MAPPER.readTree(s.html());
                List<JsonNode> nodes = root.isArray() ? new ArrayList<>() : List.of(root);
                if (root.isArray()) root.forEach(nodes::add);
                for (JsonNode n : nodes) {
                    String type = n.path("@type").asText("");
                    if (!type.equalsIgnoreCase("NewsArticle") && !type.equalsIgnoreCase("Article"))
                        continue;
                    Article a = new Article();
                    a.setUrl(url);
                    a.setParseLayer("JSON-LD");
                    a.setTitle(clean(n.path("headline").asText("")));
                    a.setDescription(clean(n.path("description").asText("")));
                    if (n.has("articleBody")) {
                        a.setContent(clean(n.get("articleBody").asText("")));
                    }
                    return a;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

//    private Article parseOgContent(Document doc, String url) {
//        Element t = doc.selectFirst("meta[property=og:title]");
//        if (t == null) return null;
//        String title = clean(t.attr("content"));
//        if (title.isBlank()) return null;
//        Article a = new Article();
//        a.setUrl(url);
//        a.setParseLayer("OG");
//        a.setTitle(title);
//        Element d = doc.selectFirst("meta[property=og:description]");
//        a.setDescription(clean(d != null ? d.attr("content") : ""));
//        String content = extractArticleContent(doc);
//        if (content != null) a.setContent(content);
//        return a;
//    }

    private Article parseMetaContent(Document doc, String url) {
        String title = clean(doc.title());
        if (title.isBlank()) return null;
        Article a = new Article();
        a.setUrl(url);
        a.setParseLayer("Meta");
        a.setTitle(title);
        Element d = doc.selectFirst("meta[name=description]");
        a.setDescription(clean(d != null ? d.attr("content") : ""));
        String content = extractArticleContent(doc);
        if (content != null) a.setContent(content);
        return a;
    }

    private Article parseBoilerpipeContent(Document doc, String url) {
        try {
            String text = ArticleExtractor.INSTANCE.getText(doc.html());
            if (text == null || text.length() < minBodyLength()) return null;
            Article a = new Article();
            a.setUrl(url);
            a.setParseLayer("Boilerpipe");
            String title = clean(doc.title());
            if (title.isBlank() && text.contains("\n")) {
                title = clean(text.substring(0, text.indexOf("\n")));
            }
            a.setTitle(title.isBlank() ? clean(text.split("\n")[0]) : title);
            a.setContent(clean(text));
            return a;
        } catch (Exception ignored) {}
        return null;
    }


    private Date extractDateFromJsonLd(Document doc) {
        for (Element s : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = MAPPER.readTree(s.html());
                List<JsonNode> nodes = root.isArray() ? new ArrayList<>() : List.of(root);
                if (root.isArray()) root.forEach(nodes::add);
                for (JsonNode n : nodes) {
                    String dp = clean(n.path("datePublished").asText(""));
                    String dm = clean(n.path("dateModified").asText(""));
                    OffsetDateTime odt = tryParseOffset(!dp.isBlank() ? dp : dm);
                    if (odt != null) {
                        return Date.from(odt.atZoneSameInstant(VN_ZONE).toInstant());
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Date extractDateFromTimeTag(Document doc) {
        Element t = doc.selectFirst("time[datetime]");
        if (t != null) {
            OffsetDateTime odt = tryParseOffset(clean(t.attr("datetime")));
            if (odt != null) {
                return Date.from(odt.atZoneSameInstant(VN_ZONE).toInstant());
            }
        }
        return null;
    }

//    private Date extractDateFromOg(Document doc) {
//        Element t = doc.selectFirst("meta[property=article:published_time]");
//        if (t != null) {
//            OffsetDateTime odt = tryParseOffset(clean(t.attr("content")));
//            if (odt != null) {
//                return Date.from(odt.atZoneSameInstant(VN_ZONE).toInstant());
//            }
//        }
//        return null;
//    }

    private Date extractDateFromMeta(Document doc) {
        Element t = doc.selectFirst("meta[name=date],meta[name=pubdate]");
        if (t != null) {
            OffsetDateTime odt = tryParseOffset(clean(t.attr("content")));
            if (odt != null) {
                return Date.from(odt.atZoneSameInstant(VN_ZONE).toInstant());
            }
        }
        return null;
    }

    private Date extractDateFromText(Document doc) {
        Matcher m = DATE_TEXT_PATTERN.matcher(doc.text());
        if (m.find()) {
            OffsetDateTime odt = tryParseOffset(m.group(1));
            if (odt != null) {
                return Date.from(odt.atZoneSameInstant(VN_ZONE).toInstant());
            }
        }
        return null;
    }

    private String extractAuthorFromJsonLd(Document doc) {
        for (Element s : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = MAPPER.readTree(s.html());
                List<JsonNode> nodes = root.isArray() ? new ArrayList<>() : List.of(root);
                if (root.isArray()) root.forEach(nodes::add);
                for (JsonNode n : nodes) {
                    if (n.has("author")) {
                        JsonNode auth = n.get("author");
                        String name = auth.isArray() ?
                                auth.get(0).path("name").asText("") :
                                auth.path("name").asText("");
                        if (!name.isBlank()) return clean(name);
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String extractAuthorFromMeta(Document doc) {
        Element t = doc.selectFirst("meta[name=author]");
        return t != null && !t.attr("content").isBlank() ? clean(t.attr("content")) : null;
    }

    private String extractAuthorFromText(Document doc) {
        Element t = doc.selectFirst("p.byline, span.author, div.author");
        return t != null ? clean(t.text()) : null;
    }

    private String extractArticleContent(Document doc) {
        Elements es = doc.select("article,div.article,div.content,div.post,div.entry-content,div.article-body");
        for (Element c : es) {
            StringBuilder sb = new StringBuilder();
            for (Element p : c.select("p")) {
                String t = clean(p.text());
                if (t.length() >= minTagLength()) sb.append(t).append("\n");
            }
            String out = sb.toString().trim();
            if (out.length() >= minBodyLength()) return out;
        }
        return null;
    }

    private String extractByTags(Document doc) {
        StringBuilder sb = new StringBuilder();
        for (Element e : doc.select("h1,h2,h3,p,span")) {
            if (e.parents().hasClass("footer") || e.parents().hasClass("ads") || e.hasClass("comment") || e.hasClass("form")) {
                continue;
            }
            String t = clean(e.text());
            if (t.length() >= minTagLength()) sb.append(t).append("\n");
        }
        return sb.toString().trim();
    }

    private String extractCategory(Document doc, String url) {
        Elements bc = doc.select("nav.breadcrumb li a,ul.breadcrumb li a");
        if (!bc.isEmpty()) return clean(bc.last().text());
        Element m = doc.selectFirst("meta[name=category],meta[property=article:section]");
        if (m != null && !m.attr("content").isBlank()) return clean(m.attr("content"));
        try {
            String path = new URI(url).getPath();
            String[] seg = path.split("/");
            if (seg.length > 1 && !seg[1].isBlank()) return clean(seg[1]);
        } catch (Exception ignored) {}
        return "";
    }

    private String clean(String s) {
        if (s == null) return "";
        return Parser.unescapeEntities(s.trim().replaceAll("\\s+", " "), true);
    }
}