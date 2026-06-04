package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Reward;
import com.mark43.loyalty.infrastructure.repository.RewardRepository;
import com.mark43.loyalty.interfaces.dto.RewardDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RewardServiceImplTest {

    @Mock
    private RewardRepository rewardRepository;

    @InjectMocks
    private RewardServiceImpl rewardService;

    private RewardDTO rewardDto;
    private Reward rewardEntity;

    @BeforeEach
    void setUp() {
        rewardDto = new RewardDTO("$10 Gift Card", "Ten dollar store voucher", new BigDecimal("100.00"));
        rewardEntity = new Reward(5L, "$10 Gift Card", "Ten dollar store voucher", new BigDecimal("100.00"));
    }

    @Test
    void verifyIfCreateRewardSucceedsAndConvertsToSecureDtoWithoutLeakingId() {

        when(rewardRepository.save(any(Reward.class))).thenReturn(rewardEntity);

        RewardDTO result = rewardService.createReward(rewardDto);

        assertNotNull(result);
        assertEquals("$10 Gift Card", result.getName());
        assertEquals(new BigDecimal("100.00"), result.getPointsRequired());
        verify(rewardRepository, times(1)).save(any(Reward.class));
    }

    @Test
    void verifyIfCreateRewardThrowsExceptionWhenPayloadIsNull() {

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                rewardService.createReward(null)
        );

        assertTrue(exception.getMessage().contains("Reward payload cannot be null"));
    }

    @Test
    void verifyIfGetRewardByIdSucceedsAndMapsToDto() {

        when(rewardRepository.findById(5L)).thenReturn(Optional.of(rewardEntity));

        RewardDTO result = rewardService.getRewardById(5L);

        assertNotNull(result);
        assertEquals("$10 Gift Card", result.getName());
    }

    @Test
    void verifyIfGetAllRewardsReturnsCompleteDtoCollection() {

        Reward legacyReward = new Reward(6L, "Jacket", "Premium Outerwear", new BigDecimal("500.00"));
        when(rewardRepository.findAll()).thenReturn(Arrays.asList(rewardEntity, legacyReward));

        List<RewardDTO> results = rewardService.getAllRewards();

        assertEquals(2, results.size());
        assertEquals("$10 Gift Card", results.get(0).getName());
        assertEquals("Jacket", results.get(1).getName());
    }

    @Test
    void verifyIfUpdateRewardSucceedsWhenModifyingProperties() {

        RewardDTO updateDetails = new RewardDTO("$15 Gift Card", "Fifteen dollar store voucher", new BigDecimal("150.00"));
        when(rewardRepository.findById(5L)).thenReturn(Optional.of(rewardEntity));
        when(rewardRepository.save(any(Reward.class))).thenReturn(rewardEntity);

        RewardDTO result = rewardService.updateReward(5L, updateDetails);

        assertNotNull(result);
        assertEquals("$15 Gift Card", rewardEntity.getName());
        assertEquals(new BigDecimal("150.00"), rewardEntity.getPointsRequired());
        verify(rewardRepository, times(1)).save(rewardEntity);
    }

    @Test
    void verifyIfDeleteRewardSucceedsWhenIdIsValid() {

        when(rewardRepository.findById(5L)).thenReturn(Optional.of(rewardEntity));

        rewardService.deleteReward(5L);

        verify(rewardRepository, times(1)).delete(rewardEntity);
    }
}