package com.mark43.loyalty.infrastructure.repository;

import com.mark43.loyalty.domain.entity.Reward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RewardRepository extends JpaRepository<Reward, Long> {

    /**
     * Looks up a reward option by its unique catalog name descriptor.
     */
    Optional<Reward> findByName(String name);
}