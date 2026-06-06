package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoyaltyCacheManager {

    // Cache customer against their email Id
    private final Map<String, CustomerDTO> balanceCache = new ConcurrentHashMap<>();

    public CustomerDTO get(String email) {
        return balanceCache.get(email);
    }

    public void put(String email, CustomerDTO dto) {
        if (email != null && dto != null) {
            balanceCache.put(email, dto);
        }
    }

    public void invalidate(String email) {
        if (email != null) {
            balanceCache.remove(email);
        }
    }

    public void clearAll() {
        balanceCache.clear();
    }
}