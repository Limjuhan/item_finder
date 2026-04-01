package com.itemfinder.dto;

import com.itemfinder.domain.price.ProductPrice;
import lombok.Getter;

@Getter
public class PriceInfoDto {
    private final String platform;
    private final Integer price;
    private final Integer originalPrice;
    private final Integer discountRate;
    private final Boolean inStock;
    private final String url;

    public PriceInfoDto(ProductPrice p) {
        this.platform = p.getPlatform();
        this.price = p.getPrice();
        this.originalPrice = p.getOriginalPrice();
        this.discountRate = p.getDiscountRate();
        this.inStock = p.getInStock();
        this.url = p.getUrl();
    }
}
