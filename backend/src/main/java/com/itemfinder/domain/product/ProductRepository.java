package com.itemfinder.domain.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.productName LIKE %:query% OR p.brand LIKE %:query% OR p.productCode LIKE %:query%")
    List<Product> searchByNameOrBrand(@Param("query") String query);

    Optional<Product> findByProductCode(String productCode);
}
