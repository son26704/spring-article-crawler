package com.dantri.crawler.queue;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UrlTask {
    private  String url;
    private  int    level;
}
