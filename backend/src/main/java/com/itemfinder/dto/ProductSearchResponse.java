package com.itemfinder.dto;

import com.itemfinder.domain.product.Product;
import lombok.Getter;

import java.util.List;

@Getter
public class ProductSearchResponse {
    private final Long id;
    private final String productName;
    private final String brand;
    private final String category;
    private final String imageUrl;
    private final List<PriceInfoDto> prices;
    private final Integer lowestPrice;

    public ProductSearchResponse(Product product, List<PriceInfoDto> prices) {
        this.id = product.getId();
        this.productName = product.getProductName();
        this.brand = product.getBrand();
        this.category = product.getCategory();
        this.imageUrl = product.getImageUrl();
        this.prices = prices;
        this.lowestPrice = prices.stream()
                .mapToInt(PriceInfoDto::getPrice)
                .min()
                .orElse(0);
    }
}
