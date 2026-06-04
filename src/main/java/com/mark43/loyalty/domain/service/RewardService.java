package com.mark43.loyalty.domain.service;

import com.mark43.loyalty.domain.entity.Reward;
import java.util.List;

public interface RewardService {
    Reward createReward(Reward reward);
    Reward getRewardById(Long rewardId);
    List<Reward> getAllRewards();
    Reward updateReward(Long rewardId, Reward rewardDetails);
    void deleteReward(Long rewardId);
}