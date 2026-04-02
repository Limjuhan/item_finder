package com.itemfinder.crawler;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/crawl")
@RequiredArgsConstructor
public class CrawlerController {

    private final MusinsaCrawlerService musinsaCrawlerService;

    @PostMapping("/musinsa")
    public ResponseEntity<Map<String, Object>> crawlMusinsa(
            @RequestParam String query) {
        int count = musinsaCrawlerService.crawl(query);
        return ResponseEntity.ok(Map.of(
                "query", query,
                "platform", "musinsa",
                "savedCount", count
        ));
    }
}
