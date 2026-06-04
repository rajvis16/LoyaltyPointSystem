package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Reward;
import com.mark43.loyalty.domain.service.RewardService;
import com.mark43.loyalty.infrastructure.repository.RewardRepository;
import com.mark43.loyalty.interfaces.dto.RewardDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {

    private final RewardRepository rewardRepository;

    @Override
    public RewardDTO createReward(RewardDTO rewardDTO) {

        if (rewardDTO == null) {
            throw new IllegalArgumentException("Reward payload cannot be null.");
        }

        log.info("Registering new loyalty reward option: {}", rewardDTO.getName());

        Reward rewardEntity = convertToEntity(rewardDTO);
        Reward savedReward = rewardRepository.save(rewardEntity);

        return convertToDto(savedReward);
    }

    @Override
    public RewardDTO getRewardById(Long rewardId) {

        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Reward option not found with ID: " + rewardId));
        return convertToDto(reward);
    }

    @Override
    public List<RewardDTO> getAllRewards() {

        return rewardRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    public RewardDTO updateReward(Long rewardId, RewardDTO rewardDetails) {

        if (rewardDetails == null) {
            throw new IllegalArgumentException("Reward update payload cannot be null.");
        }

        Reward existingReward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Reward option not found with ID: " + rewardId));

        // Update properties from the incoming DTO boundary
        existingReward.setName(rewardDetails.getName());
        existingReward.setDescription(rewardDetails.getDescription());
        existingReward.setPointsRequired(rewardDetails.getPointsRequired());

        log.info("Updating reward catalog item ID: {}", rewardId);
        Reward updatedReward = rewardRepository.save(existingReward);

        return convertToDto(updatedReward);
    }

    @Override
    public void deleteReward(Long rewardId) {

        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Reward option not found with ID: " + rewardId));

        rewardRepository.delete(reward);

        log.info("Successfully withdrew reward option ID {} from system catalog", rewardId);
    }

    private Reward convertToEntity(RewardDTO dto) {

        Reward entity = new Reward();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setPointsRequired(dto.getPointsRequired());
        return entity;
    }

    private RewardDTO convertToDto(Reward entity) {

        RewardDTO dto = new RewardDTO();

        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setPointsRequired(entity.getPointsRequired());

        return dto;
    }
}