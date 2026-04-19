package com.itemfinder.cache;

import com.itemfinder.dto.ProductSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SearchCache {

    private static final long TTL_MILLIS = 5 * 60 * 1000L; // 5분

    private record CacheEntry(List<ProductSearchResponse> products, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > TTL_MILLIS;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();

    public List<ProductSearchResponse> get(String keyword) {
        String key = normalize(keyword);
        CacheEntry entry = store.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            store.remove(key);
            log.debug("[Cache] Expired entry removed for keyword: {}", key);
            return null;
        }
        log.debug("[Cache] HIT for keyword: {}", key);
        return entry.products();
    }

    public void put(String keyword, List<ProductSearchResponse> products) {
        String key = normalize(keyword);
        store.put(key, new CacheEntry(List.copyOf(products), System.currentTimeMillis()));
        log.debug("[Cache] Stored {} products for keyword: {}", products.size(), key);
    }

    public boolean has(String keyword) {
        return get(keyword) != null;
    }

    private String normalize(String keyword) {
        return keyword.trim().toLowerCase();
    }
}
