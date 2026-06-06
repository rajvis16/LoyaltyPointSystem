package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.service.RewardService;
import com.mark43.loyalty.interfaces.dto.RewardDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rewards")
@Log4j2
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    /**
     * POST /api/v1/rewards
     * Registers a new reward option into the system catalog.
     * Returns 201 Created to pass your integration test expectations.
     */
    @PostMapping
    public ResponseEntity<RewardDTO> createReward(@Valid @RequestBody RewardDTO rewardDTO) {
        log.info("REST request to create loyalty reward option: {}", rewardDTO.getName());
        RewardDTO createdReward = rewardService.createReward(rewardDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdReward);
    }

    /**
     * GET /api/v1/rewards/{id}
     * Retrieves specific reward metadata by database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RewardDTO> getRewardById(@PathVariable Long id) {
        log.info("REST request to get reward configuration options for ID: {}", id);
        RewardDTO reward = rewardService.getRewardById(id);
        return ResponseEntity.ok(reward);
    }

    /**
     * GET /api/v1/rewards
     * Fetches the entire available loyalty rewards catalog catalog matrix.
     */
    @GetMapping
    public ResponseEntity<List<RewardDTO>> getAllRewards() {
        log.info("REST request to fetch entire system rewards matrix catalog");
        List<RewardDTO> rewards = rewardService.getAllRewards();
        return ResponseEntity.ok(rewards);
    }

    /**
     * PUT /api/v1/rewards/{id}
     * Modifies properties of an existing catalog entry.
     */
    @PutMapping("/{id}")
    public ResponseEntity<RewardDTO> updateReward(@PathVariable Long id, @Valid @RequestBody RewardDTO rewardDTO) {
        log.info("REST request to update catalog properties for reward item ID: {}", id);
        RewardDTO updatedReward = rewardService.updateReward(id, rewardDTO);
        return ResponseEntity.ok(updatedReward);
    }

    /**
     * DELETE /api/v1/rewards/{id}
     * Removes an option from active redemptions visibility schemas.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReward(@PathVariable Long id) {
        log.info("REST request to withdraw reward item configuration option ID: {}", id);
        rewardService.deleteReward(id);
        return ResponseEntity.noContent().build();
    }
}