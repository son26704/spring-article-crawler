package com.dantri.crawler.queue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UrlTask {
    private final String url;
    private final int    level;
}
