package com.itemfinder.dto;

import lombok.Getter;

@Getter
public class ProductSearchResponse {
    private final String platform;
    private final String platformProductId;
    private final String productName;
    private final String brand;
    private final String imageUrl;
    private final Integer price;
    private final Integer originalPrice;
    private final Integer discountRate;
    private final String url;

    public ProductSearchResponse(
            String platform, String platformProductId,
            String productName, String brand, String imageUrl,
            Integer price, Integer originalPrice, Integer discountRate, String url) {
        this.platform = platform;
        this.platformProductId = platformProductId;
        this.productName = productName;
        this.brand = brand;
        this.imageUrl = imageUrl;
        this.price = price;
        this.originalPrice = originalPrice;
        this.discountRate = discountRate;
        this.url = url;
    }
}
