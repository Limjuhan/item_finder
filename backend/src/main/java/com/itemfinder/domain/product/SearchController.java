package com.itemfinder.domain.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itemfinder.cache.SearchCache;
import com.itemfinder.crawler.PlatformCrawler;
import com.itemfinder.domain.search.SearchHistory;
import com.itemfinder.domain.search.SearchHistoryRepository;
import com.itemfinder.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final List<PlatformCrawler> crawlers;
    private final SearchCache searchCache;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() < 2) {
            SseEmitter emitter = new SseEmitter(5000L);
            sendAndComplete(emitter, "error", "keyword must be at least 2 characters");
            return emitter;
        }

        String normalizedKeyword = keyword.trim();
        SseEmitter emitter = new SseEmitter(60_000L);

        // 캐시 히트
        List<ProductSearchResponse> cached = searchCache.get(normalizedKeyword);
        if (cached != null) {
            log.info("[SearchController] Cache HIT for keyword: {}", normalizedKeyword);
            sendAndComplete(emitter, "data", cached);
            return emitter;
        }

        // 캐시 미스 — 별도 스레드에서 크롤링
        executor.submit(() -> {
            List<ProductSearchResponse> allResults = new ArrayList<>();
            try {
                for (PlatformCrawler crawler : crawlers) {
                    try {
                        List<ProductSearchResponse> results = crawler.crawl(normalizedKeyword);
                        allResults.addAll(results);
                        // 플랫폼 크롤링 완료 즉시 전송
                        emitter.send(
                            SseEmitter.event()
                                .name("data")
                                .data(objectMapper.writeValueAsString(results))
                        );
                        log.info("[SearchController] Sent {} products for keyword: {}",
                                results.size(), normalizedKeyword);
                    } catch (Exception e) {
                        log.warn("[SearchController] Crawler failed: {}", e.getMessage());
                    }
                }

                // 전체 결과 캐시 저장
                if (!allResults.isEmpty()) {
                    searchCache.put(normalizedKeyword, allResults);
                }

                // SearchHistory 저장
                saveSearchHistory(normalizedKeyword);

                // 완료 신호
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();

            } catch (IOException e) {
                log.warn("[SearchController] SSE write failed (client disconnected?): {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void saveSearchHistory(String keyword) {
        try {
            searchHistoryRepository.findByKeyword(keyword)
                    .ifPresentOrElse(
                            SearchHistory::updateCrawledTime,
                            () -> searchHistoryRepository.save(new SearchHistory(keyword))
                    );
        } catch (DataIntegrityViolationException e) {
            log.debug("[SearchController] Search history duplicate for keyword: {}", keyword);
        } catch (Exception e) {
            log.warn("[SearchController] Failed to save search history: {}", e.getMessage());
        }
    }

    private void sendAndComplete(SseEmitter emitter, String eventName, Object data) {
        try {
            String payload = data instanceof String s ? s : objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
