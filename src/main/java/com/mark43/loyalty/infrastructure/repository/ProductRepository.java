package com.mark43.loyalty.infrastructure.repository;

import com.mark43.loyalty.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByName(String name);

    @Query("SELECT COALESCE(SUM(p.price), 0.00) FROM Product p WHERE p.productId IN :productIds")
    BigDecimal calculateTotalSumByIds(@Param("productIds") List<Long> productIds);

    List<Product> findByNameIn(Collection<String> names);
}