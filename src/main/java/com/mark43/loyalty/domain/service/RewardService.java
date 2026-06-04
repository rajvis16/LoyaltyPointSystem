package com.mark43.loyalty.domain.service;

import com.mark43.loyalty.interfaces.dto.RewardDTO;
import java.util.List;

public interface RewardService {

    /**
     * Registers a new reward option in the active system catalog using the incoming payload state.
     *
     * @param reward The data transfer object holding the new reward option details.
     * @return The fully populated RewardDTO matching the committed state.
     */
    RewardDTO createReward(RewardDTO reward);

    /**
     * Retrieves a single reward option configuration by its primary key database ID.
     *
     * @param rewardId The unique identifier of the target reward.
     * @return The matching RewardDTO payload.
     * @throws IllegalArgumentException if no reward exists with the given ID.
     */
    RewardDTO getRewardById(Long rewardId);

    /**
     * Fetches all registered reward options currently available in the system catalog.
     *
     * @return A list of all active configurations mapped to RewardDTO formats.
     */
    List<RewardDTO> getAllRewards();

    /**
     * Updates the configurable properties of an existing reward option.
     *
     * @param rewardId The unique identifier of the reward being modified.
     * @param rewardDetails The data transfer object containing the updated parameters.
     * @return The updated RewardDTO snapshot representing the saved database state.
     * @throws IllegalArgumentException if the target reward is not found or payload is invalid.
     */
    RewardDTO updateReward(Long rewardId, RewardDTO rewardDetails);

    /**
     * Permanently removes a reward option from the active catalog.
     *
     * @param rewardId The unique identifier of the reward to be withdrawn.
     * @throws IllegalArgumentException if no reward option matches the specified ID.
     */
    void deleteReward(Long rewardId);
}