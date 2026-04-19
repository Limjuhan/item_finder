package com.itemfinder.crawler;

import com.itemfinder.dto.ProductSearchResponse;
import java.util.List;

public interface PlatformCrawler {
    List<ProductSearchResponse> crawl(String keyword);
}
