package com.course.ecommerce.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.course.ecommerce.entity.Product;
import com.course.ecommerce.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);
    private static final String INDEX_NAME = "products";

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 启动时全量同步 MySQL → ES
     */
    @PostConstruct
    public void syncAllProducts() {
        try {
            List<Product> products = productMapper.selectList(null);
            log.info("Syncing {} products to ElasticSearch", products.size());
            for (Product p : products) {
                indexProduct(p);
            }
            log.info("Product sync to ElasticSearch complete");
        } catch (Exception e) {
            log.warn("Failed to sync products to ES on startup: {}", e.getMessage());
        }
    }

    /**
     * 索引单个商品
     */
    public void indexProduct(Product product) {
        try {
            ProductDocument doc = toDocument(product);
            esClient.index(IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(product.getId()))
                    .document(doc)
            ));
        } catch (IOException e) {
            log.error("Failed to index product {}: {}", product.getId(), e.getMessage());
        }
    }

    /**
     * 关键词全文搜索
     */
    public List<ProductDocument> search(String keyword) {
        try {
            Query matchQuery = MatchQuery.of(m -> m
                    .field("name")
                    .query(keyword)
            )._toQuery();

            SearchResponse<ProductDocument> response = esClient.search(
                    SearchRequest.of(s -> s
                            .index(INDEX_NAME)
                            .query(matchQuery)
                            .size(20)
                    ),
                    ProductDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Search failed for keyword '{}': {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    private ProductDocument toDocument(Product p) {
        return new ProductDocument(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStock(),
                p.getCategory(),
                p.getImageUrl(),
                p.getStatus()
        );
    }
}
