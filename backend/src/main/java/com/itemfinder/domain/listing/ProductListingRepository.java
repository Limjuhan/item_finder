package com.itemfinder.domain.listing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductListingRepository extends JpaRepository<ProductListing, Long> {

    List<ProductListing> findByProductNameContainingIgnoreCase(String productName);

    List<ProductListing> findByBrandContainingIgnoreCase(String brand);

    Optional<ProductListing> findByPlatformAndPlatformProductId(String platform, String platformProductId);

    @Query("SELECT p FROM ProductListing p WHERE " +
            "p.productName LIKE CONCAT('%', :keyword, '%') OR " +
            "p.brand LIKE CONCAT('%', :keyword, '%') " +
            "ORDER BY p.price ASC")
    List<ProductListing> searchByKeyword(@Param("keyword") String keyword);
}
