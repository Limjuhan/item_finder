package com.itemfinder.domain.product;

import com.itemfinder.crawler.MusinsaCrawlerService;
import com.itemfinder.domain.price.ProductPriceRepository;
import com.itemfinder.domain.search.SearchHistory;
import com.itemfinder.domain.search.SearchHistoryRepository;
import com.itemfinder.dto.PriceInfoDto;
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

    private static final int CACHE_EXPIRE_HOURS = 6;

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final MusinsaCrawlerService musinsaCrawlerService;

    public List<ProductSearchResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String keyword = query.trim();
        SearchHistory history = searchHistoryRepository.findByKeyword(keyword).orElse(null);

        if (history == null) {
            log.info("New keyword '{}' — crawling Musinsa", keyword);
            musinsaCrawlerService.crawl(keyword);
            try {
                searchHistoryRepository.save(new SearchHistory(keyword));
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 이미 저장된 경우 무시
                log.debug("SearchHistory already saved for keyword '{}'", keyword);
            }
        } else if (history.isExpired(CACHE_EXPIRE_HOURS)) {
            log.info("Keyword '{}' expired — re-crawling Musinsa", keyword);
            musinsaCrawlerService.crawl(keyword);
            history.updateCrawledTime();
            searchHistoryRepository.save(history);
        } else {
            log.info("Keyword '{}' — returning cached DB results", keyword);
        }

        return queryFromDb(keyword);
    }

    private List<ProductSearchResponse> queryFromDb(String keyword) {
        List<Product> products = productRepository.searchByNameOrBrand(keyword);

        return products.stream()
                .map(product -> {
                    List<PriceInfoDto> prices = productPriceRepository
                            .findByProductId(product.getId())
                            .stream()
                            .map(PriceInfoDto::new)
                            .sorted(Comparator.comparingInt(PriceInfoDto::getPrice))
                            .toList();
                    return new ProductSearchResponse(product, prices);
                })
                .filter(r -> !r.getPrices().isEmpty())
                .sorted(Comparator.comparingInt(ProductSearchResponse::getLowestPrice))
                .toList();
    }
}
