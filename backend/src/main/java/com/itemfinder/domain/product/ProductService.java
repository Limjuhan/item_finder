package com.itemfinder.domain.product;

import com.itemfinder.crawler.PlatformCrawler;
import com.itemfinder.domain.listing.ProductListingRepository;
import com.itemfinder.domain.search.SearchHistory;
import com.itemfinder.domain.search.SearchHistoryRepository;
import com.itemfinder.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductListingRepository productListingRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final List<PlatformCrawler> crawlers;

    public List<ProductSearchResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String keyword = query.trim();
        log.info("Keyword '{}' — crawling all platforms", keyword);
        crawlers.forEach(c -> c.crawl(keyword));

        // search_history 저장 (Phase 2 스케줄러용: 자주 검색된 키워드 추적)
        try {
            searchHistoryRepository.findByKeyword(keyword)
                    .ifPresentOrElse(
                            sh -> sh.updateCrawledTime(),
                            () -> searchHistoryRepository.save(new SearchHistory(keyword))
                    );
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 중복 INSERT 발생 시 무시 (이미 크롤링 데이터는 저장됨)
            log.debug("Search history already exists for keyword: {}", keyword);
        }

        return queryFromDb(keyword);
    }

    private List<ProductSearchResponse> queryFromDb(String keyword) {
        return productListingRepository.searchByKeyword(keyword).stream()
                .map(ProductSearchResponse::new)
                .sorted(Comparator.comparingInt(ProductSearchResponse::getPrice))
                .toList();
    }
}
