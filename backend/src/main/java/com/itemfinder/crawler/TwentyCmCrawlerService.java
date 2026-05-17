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

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Override
    public List<ProductSearchResponse> crawl(String keyword) {
        long start = System.currentTimeMillis();
        log.info("[29cm] Starting crawl for keyword: {}", keyword);

        List<ProductSearchResponse> results = fetchFrom29cm(keyword);

        log.info("[29cm] Crawl completed in {}ms, found {} products",
                System.currentTimeMillis() - start, results.size());
        return results;
    }

    private List<ProductSearchResponse> fetchFrom29cm(String query) {
        List<ProductSearchResponse> results = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = SEARCH_API + "?keyword=" + encoded + "&limit=50&offset=0";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.29cm.co.kr/store/search?keyword=" + encoded)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[29cm] API returned status {}", response.statusCode());
                return results;
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
        } catch (Exception e) {
            log.error("[29cm] Crawl failed: {}", e.getMessage());
        }
        return results;
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;
        if (imageUrl.startsWith("http")) return imageUrl;
        return IMAGE_URL_PREFIX + imageUrl;
    }
}
