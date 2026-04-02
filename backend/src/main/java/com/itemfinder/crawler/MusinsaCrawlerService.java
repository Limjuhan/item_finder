package com.itemfinder.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itemfinder.domain.listing.ProductListing;
import com.itemfinder.domain.listing.ProductListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusinsaCrawlerService implements PlatformCrawler {

    private static final String PLATFORM = "musinsa";
    private static final String SEARCH_API = "https://api.musinsa.com/api2/dp/v1/plp/goods";

    private final ProductListingRepository productListingRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public int crawl(String query) {
        long startTime = System.currentTimeMillis();
        log.info("[1] Starting Musinsa crawl for query: {}", query);

        long fetchStart = System.currentTimeMillis();
        List<CrawledProduct> crawledProducts = fetchFromMusinsa(query);
        long fetchTime = System.currentTimeMillis() - fetchStart;
        log.info("[2] API fetch completed in {}ms, found {} products", fetchTime, crawledProducts.size());

        if (crawledProducts.isEmpty()) {
            return 0;
        }

        long saveStart = System.currentTimeMillis();
        int saved = 0;
        for (CrawledProduct cp : crawledProducts) {
            try {
                upsert(cp);
                saved++;
            } catch (Exception e) {
                log.warn("Failed to save '{}': {}", cp.name(), e.getMessage());
            }
        }
        long saveTime = System.currentTimeMillis() - saveStart;
        log.info("[3] DB save completed in {}ms, saved {} products", saveTime, saved);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[4] Total crawl time: {}ms (fetch: {}ms + save: {}ms)", totalTime, fetchTime, saveTime);
        return saved;
    }

    private List<CrawledProduct> fetchFromMusinsa(String query) {
        List<CrawledProduct> results = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = SEARCH_API + "?keyword=" + encoded
                    + "&gf=M&pageNumber=1&pageSize=50&sortCode=POPULAR&caller=SEARCH";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.musinsa.com/search/goods?keyword=" + encoded)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Musinsa API returned status {}", response.statusCode());
                return results;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode list = root.path("data").path("list");

            int maxProducts = Math.min(10, list.size()); // 최대 10개
            for (int i = 0; i < maxProducts; i++) {
                JsonNode item = list.get(i);
                if (item.path("isSoldOut").asBoolean(false)) continue;

                String goodsNo = item.path("goodsNo").asText();
                String name = item.path("goodsName").asText();
                String brand = item.path("brandName").asText();
                String imageUrl = item.path("thumbnail").asText();
                String productUrl = item.path("goodsLinkUrl").asText();
                int price = item.path("price").asInt();
                int normalPrice = item.path("normalPrice").asInt();
                int saleRate = item.path("saleRate").asInt();

                if (name.isBlank() || price == 0) continue;

                results.add(new CrawledProduct(
                        goodsNo, name, brand, imageUrl, productUrl,
                        price, normalPrice == price ? null : normalPrice,
                        saleRate == 0 ? null : saleRate
                ));
            }
        } catch (Exception e) {
            log.error("Crawl failed: {}", e.getMessage());
        }
        return results;
    }

    // 상품 1건을 독립 트랜잭션으로 저장 — 실패해도 다른 상품 저장에 영향 없음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(CrawledProduct cp) {
        ProductListing listing = productListingRepository
                .findByPlatformAndPlatformProductId(PLATFORM, cp.productCode())
                .orElse(new ProductListing());

        listing.setPlatform(PLATFORM);
        listing.setPlatformProductId(cp.productCode());
        listing.setProductName(cp.name());
        listing.setBrand(cp.brand());
        listing.setImageUrl(cp.imageUrl());
        listing.setPrice(cp.price());
        listing.setOriginalPrice(cp.originalPrice());
        listing.setDiscountRate(cp.discountRate());
        listing.setUrl(cp.url());
        listing.setInStock(true);

        productListingRepository.save(listing);
    }

    record CrawledProduct(
            String productCode,
            String name,
            String brand,
            String imageUrl,
            String url,
            int price,
            Integer originalPrice,
            Integer discountRate
    ) {}
}
