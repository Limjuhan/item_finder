package com.itemfinder.domain.price;

import com.itemfinder.domain.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_prices", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "platform"})
})
@Getter
@Setter
@NoArgsConstructor
public class ProductPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String platform;

    @Column(name = "platform_product_id")
    private String platformProductId;

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
}
