package com.itemfinder.domain.product;

import com.itemfinder.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/search")
    public ResponseEntity<List<ProductSearchResponse>> search(
            @RequestParam String query) {
        List<ProductSearchResponse> results = productService.search(query);
        return ResponseEntity.ok(results);
    }
}
