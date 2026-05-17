package com.itemfinder.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itemfinder.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwentyCmCrawlerService implements PlatformCrawler {

    private static final String PLATFORM = "29cm";
    private static final String SEARCH_API = "https://search-api.29cm.co.kr/api/v4/products";
    private static final String PRODUCT_URL_PREFIX = "https://product.29cm.co.kr/catalog/";
    private static final String IMAGE_URL_PREFIX = "https://img.29cm.co.kr";
    private static final int FAILURE_THRESHOLD = 3;
    private static final long DELAY_MILLIS = 30_000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final CircuitBreaker circuitBreaker =
            new CircuitBreaker(PLATFORM, FAILURE_THRESHOLD, DELAY_MILLIS);

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public List<ProductSearchResponse> crawl(String keyword) {
        if (!circuitBreaker.allowRequest()) {
            log.warn("[29cm] Circuit OPEN — skipping request");
            throw new CrawlerException("Circuit OPEN");
        }

        long start = System.currentTimeMillis();
        log.info("[29cm] Starting crawl for keyword: {}", keyword);

        try {
            List<ProductSearchResponse> results = fetchFrom29cm(keyword);
            circuitBreaker.recordSuccess();
            log.info("[29cm] Crawl completed in {}ms, found {} products",
                    System.currentTimeMillis() - start, results.size());
            return results;
        } catch (CrawlerException e) {
            throw e;
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.error("[29cm] Crawl failed: {}", e.getMessage());
            throw new CrawlerException(e.getMessage(), e);
        }
    }

    private List<ProductSearchResponse> fetchFrom29cm(String query) throws Exception {
        List<ProductSearchResponse> results = new ArrayList<>();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = SEARCH_API + "?keyword=" + encoded + "&limit=50&offset=0";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.29cm.co.kr/store/search?keyword=" + encoded)
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new CrawlerException("API returned status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode data = root.path("data");

        int maxProducts = Math.min(10, data.size());
        for (int i = 0; i < maxProducts; i++) {
            JsonNode item = data.get(i);
            if (item.path("isSoldOut").asBoolean(false)) continue;

            String itemNo = item.path("itemNo").asText();
            String name = item.path("itemName").asText();
            String brand = item.path("frontBrandNameKor").asText();
            String imageUrl = resolveImageUrl(item.path("imageUrl").asText());
            String productUrl = PRODUCT_URL_PREFIX + itemNo;

            JsonNode saleInfo = item.path("saleInfoV2");
            int price = saleInfo.path("totalSellPrice").asInt();
            int originalPrice = saleInfo.path("consumerPrice").asInt();
            int saleRate = saleInfo.path("totalSaleRate").asInt();

            if (name.isBlank() || price == 0) continue;

            results.add(new ProductSearchResponse(
                    PLATFORM,
                    itemNo,
                    name,
                    brand,
                    imageUrl,
                    price,
                    originalPrice == price ? null : originalPrice,
                    saleRate == 0 ? null : saleRate,
                    productUrl
            ));
        }
        return results;
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;
        if (imageUrl.startsWith("http")) return imageUrl;
        return IMAGE_URL_PREFIX + imageUrl;
    }
}
