package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Reward;
import com.mark43.loyalty.domain.service.RewardService;
import com.mark43.loyalty.infrastructure.repository.RewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.List;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {

    private final RewardRepository rewardRepository;

    @Override
    public Reward createReward(Reward reward) {
        log.info("Registering new reward option: {} requiring {} points", reward.getName(), reward.getPointsRequired());
        return rewardRepository.save(reward);
    }

    @Override
    public Reward getRewardById(Long rewardId) {
        return rewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Reward option not found with ID: " + rewardId));
    }

    @Override
    public List<Reward> getAllRewards() {
        return rewardRepository.findAll();
    }

    @Override
    public Reward updateReward(Long rewardId, Reward rewardDetails) {
        Reward existingReward = getRewardById(rewardId);

        existingReward.setName(rewardDetails.getName());
        existingReward.setPointsRequired(rewardDetails.getPointsRequired());

        log.info("Updating reward tier configuration for ID: {}", rewardId);
        return rewardRepository.save(existingReward);
    }

    @Override
    public void deleteReward(Long rewardId) {
        Reward reward = getRewardById(rewardId);
        rewardRepository.delete(reward);
        log.info("Withdrew reward option ID {} from system", rewardId);
    }
}