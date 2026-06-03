package com.mark43.loyalty.infrastructure.repository;

import com.mark43.loyalty.domain.entity.RewardCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RewardCatalogRepository extends JpaRepository<RewardCatalog, Long> {

    /**
     * Looks up a reward option by its unique catalog name descriptor.
     */
    Optional<RewardCatalog> findByName(String name);
}