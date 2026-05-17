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
public class MusinsaCrawlerService implements PlatformCrawler {

    private static final String PLATFORM = "musinsa";
    private static final String SEARCH_API = "https://api.musinsa.com/api2/dp/v1/plp/goods";
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
            log.warn("[Musinsa] Circuit OPEN — skipping request");
            throw new CrawlerException("Circuit OPEN");
        }

        long start = System.currentTimeMillis();
        log.info("[Musinsa] Starting crawl for keyword: {}", keyword);

        try {
            List<ProductSearchResponse> results = fetchFromMusinsa(keyword);
            circuitBreaker.recordSuccess();
            log.info("[Musinsa] Crawl completed in {}ms, found {} products",
                    System.currentTimeMillis() - start, results.size());
            return results;
        } catch (CrawlerException e) {
            throw e;
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.error("[Musinsa] Crawl failed: {}", e.getMessage());
            throw new CrawlerException(e.getMessage(), e);
        }
    }

    private List<ProductSearchResponse> fetchFromMusinsa(String query) throws Exception {
        List<ProductSearchResponse> results = new ArrayList<>();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = SEARCH_API + "?keyword=" + encoded
                + "&gf=M&pageNumber=1&pageSize=50&sortCode=POPULAR&caller=SEARCH";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.musinsa.com/search/goods?keyword=" + encoded)
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
        JsonNode list = root.path("data").path("list");

        int maxProducts = Math.min(10, list.size());
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

            results.add(new ProductSearchResponse(
                    PLATFORM,
                    goodsNo,
                    name,
                    brand,
                    imageUrl,
                    price,
                    normalPrice == price ? null : normalPrice,
                    saleRate == 0 ? null : saleRate,
                    productUrl
            ));
        }
        return results;
    }
}
