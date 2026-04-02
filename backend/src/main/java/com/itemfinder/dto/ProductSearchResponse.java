package com.itemfinder.dto;

import com.itemfinder.domain.listing.ProductListing;
import lombok.Getter;

@Getter
public class ProductSearchResponse {
    private final Long id;
    private final String platform;
    private final String productName;
    private final String brand;
    private final String imageUrl;
    private final Integer price;
    private final Integer originalPrice;
    private final Integer discountRate;
    private final String url;
    private final Boolean inStock;

    public ProductSearchResponse(ProductListing listing) {
        this.id = listing.getId();
        this.platform = listing.getPlatform();
        this.productName = listing.getProductName();
        this.brand = listing.getBrand();
        this.imageUrl = listing.getImageUrl();
        this.price = listing.getPrice();
        this.originalPrice = listing.getOriginalPrice();
        this.discountRate = listing.getDiscountRate();
        this.url = listing.getUrl();
        this.inStock = listing.getInStock();
    }
}
