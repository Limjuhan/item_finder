package com.itemfinder.domain.price;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    List<ProductPrice> findByProductId(Long productId);

    Optional<ProductPrice> findByProductIdAndPlatform(Long productId, String platform);
}
