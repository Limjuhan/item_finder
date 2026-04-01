package com.itemfinder.domain.product;

import com.itemfinder.crawler.PlatformCrawler;
import com.itemfinder.domain.price.ProductPriceRepository;
import com.itemfinder.dto.PriceInfoDto;
import com.itemfinder.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final List<PlatformCrawler> crawlers;

    public List<ProductSearchResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String keyword = query.trim();
        log.info("Keyword '{}' — crawling all platforms", keyword);
        crawlers.forEach(c -> c.crawl(keyword));

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
