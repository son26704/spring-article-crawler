package com.dantri.crawler.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    private String url;
    private String title;
    private String description;
    private String content;
    private Date publishTime;
    private String author;
    private String category;
    private String parseLayer;
}