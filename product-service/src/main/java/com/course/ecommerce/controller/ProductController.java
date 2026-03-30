package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.entity.Product;
import com.course.ecommerce.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口
 * GET /api/products/{id}  商品详情
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private com.course.ecommerce.elasticsearch.ProductSearchService productSearchService;

    @GetMapping("/{id}")
    public Result<?> getProduct(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return Result.success(product);
    }

    @GetMapping("/health")
    public Result<?> health() {
        return Result.success("product-service is running");
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false, defaultValue = "") String q) {
        if (q == null || q.trim().isEmpty()) {
            return Result.success(java.util.Collections.emptyList());
        }
        return Result.success(productSearchService.search(q.trim()));
    }
}
