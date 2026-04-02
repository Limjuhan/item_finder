package com.itemfinder.domain.listing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_listings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"platform", "platform_product_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ProductListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String platform;  // musinsa, 29cm, coupang 등

    @Column(nullable = false)
    private String platformProductId;  // 플랫폼 내부 상품ID (goodsNo, productId 등)

    @Column(nullable = false)
    private String productName;  // 플랫폼별 상품명

    @Column(nullable = false)
    private String brand;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "original_price")
    private Integer originalPrice;

    @Column(name = "discount_rate")
    private Integer discountRate;

    @Column(name = "in_stock")
    private Boolean inStock = true;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}
