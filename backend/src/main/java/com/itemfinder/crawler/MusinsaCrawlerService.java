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
public class MusinsaCrawlerService implements PlatformCrawler {

    private static final String PLATFORM = "musinsa";
    private static final String SEARCH_API = "https://api.musinsa.com/api2/dp/v1/plp/goods";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Override
    public List<ProductSearchResponse> crawl(String keyword) {
        long start = System.currentTimeMillis();
        log.info("[Musinsa] Starting crawl for keyword: {}", keyword);

        List<ProductSearchResponse> results = fetchFromMusinsa(keyword);

        log.info("[Musinsa] Crawl completed in {}ms, found {} products",
                System.currentTimeMillis() - start, results.size());
        return results;
    }

    private List<ProductSearchResponse> fetchFromMusinsa(String query) {
        List<ProductSearchResponse> results = new ArrayList<>();
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
                log.error("[Musinsa] API returned status {}", response.statusCode());
                return results;
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
        } catch (Exception e) {
            log.error("[Musinsa] Crawl failed: {}", e.getMessage());
        }
        return results;
    }
}
