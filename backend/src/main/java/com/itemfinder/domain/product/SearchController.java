package com.itemfinder.domain.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itemfinder.cache.SearchCache;
import com.itemfinder.crawler.PlatformCrawler;
import com.itemfinder.domain.search.SearchHistory;
import com.itemfinder.domain.search.SearchHistoryRepository;
import com.itemfinder.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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

    // 동시 검색 요청 처리 전용 (최대 5건)
    private final ExecutorService requestExecutor = Executors.newFixedThreadPool(5);
    // 플랫폼 병렬 크롤링 전용 (플랫폼 수 2 × 동시 검색 5)
    private final ExecutorService crawlExecutor = Executors.newFixedThreadPool(10);

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() < 2) {
            SseEmitter emitter = new SseEmitter(5000L);
            sendAndComplete(emitter, "error", "keyword must be at least 2 characters");
            return emitter;
        }

        String normalizedKeyword = keyword.trim();
        SseEmitter emitter = new SseEmitter(60_000L);

        List<ProductSearchResponse> cached = searchCache.get(normalizedKeyword);
        if (cached != null) {
            log.info("[SearchController] Cache HIT for keyword: {}", normalizedKeyword);
            sendAndComplete(emitter, "data", cached);
            return emitter;
        }

        requestExecutor.submit(() -> {
            List<ProductSearchResponse> allResults = new CopyOnWriteArrayList<>();
            List<String> failedPlatforms = new CopyOnWriteArrayList<>();

            try {
                // 모든 플랫폼 병렬 크롤링 시작
                List<CompletableFuture<Void>> futures = crawlers.stream()
                        .map(crawler -> CompletableFuture.runAsync(() -> {
                            try {
                                List<ProductSearchResponse> results = crawler.crawl(normalizedKeyword);
                                allResults.addAll(results);
                                synchronized (emitter) {
                                    emitter.send(SseEmitter.event()
                                            .name("data")
                                            .data(objectMapper.writeValueAsString(results)));
                                }
                                log.info("[SearchController] Sent {} products from {} for keyword: {}",
                                        results.size(), crawler.getPlatformName(), normalizedKeyword);
                            } catch (Exception e) {
                                failedPlatforms.add(crawler.getPlatformName());
                                log.warn("[SearchController] {} crawler failed: {}",
                                        crawler.getPlatformName(), e.getMessage());
                            }
                        }, crawlExecutor))
                        .toList();

                // 전체 완료 대기
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                if (!allResults.isEmpty()) {
                    searchCache.put(normalizedKeyword, allResults);
                }

                saveSearchHistory(normalizedKeyword);

                String donePayload = objectMapper.writeValueAsString(
                        Map.of("failedPlatforms", failedPlatforms));
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name("done").data(donePayload));
                }
                emitter.complete();

            } catch (Exception e) {
                log.warn("[SearchController] SSE write failed (client disconnected?): {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/top10")
    public List<String> top10() {
        return searchHistoryRepository.findTop10Keywords(LocalDateTime.now().minusWeeks(1));
    }

    private void saveSearchHistory(String keyword) {
        try {
            searchHistoryRepository.save(new SearchHistory(keyword));
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
