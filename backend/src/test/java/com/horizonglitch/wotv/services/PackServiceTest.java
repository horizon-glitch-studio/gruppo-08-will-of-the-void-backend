package com.horizonglitch.wotv.services;

import com.horizonglitch.wotv.exceptions.InsufficientShardsException;
import com.horizonglitch.wotv.exceptions.PackNotFoundException;
import com.horizonglitch.wotv.exceptions.PackUnavailableException;
import com.horizonglitch.wotv.exceptions.PlayerNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PackServiceTest {
    private JdbcTemplate jdbc;
    private PackService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new PackService(jdbc, new Random(7));
    }

    @Test
    void openingIsDeclaredTransactional() throws NoSuchMethodException {
        assertTrue(PackService.class.getMethod("open", String.class, int.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void unknownPlayerFailsBeforeLookingUpPackOrWriting() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThrows(PlayerNotFoundException.class, () -> service.open("missing", 9));

        verify(jdbc, times(1)).queryForList(anyString(), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void unknownPackDoesNotWriteAnything() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(player(1200))).thenReturn(List.of());

        assertThrows(PackNotFoundException.class, () -> service.open("summoner", 404));

        verify(jdbc, times(2)).queryForList(anyString(), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void unavailableEventPackDoesNotWriteAnything() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(player(1200))).thenReturn(List.of(pack(false, 300)));

        assertThrows(PackUnavailableException.class, () -> service.open("summoner", 3));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void insufficientBalanceDoesNotDebitOrCreateOpening() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(player(299))).thenReturn(List.of(pack(true, 300)));

        var exception = assertThrows(InsufficientShardsException.class, () -> service.open("summoner", 3));

        assertEquals(299, exception.getShardBalance());
        assertEquals(300, exception.getShardCost());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private static Map<String, Object> player(int balance) {
        return Map.of("id", 11L, "credit_balance", balance,
                "champion_pack_misses", 0, "elect_standard_misses", 0);
    }

    private static Map<String, Object> pack(boolean available, int cost) {
        return Map.of("id", 3, "name", "Standard", "credit_cost", cost,
                "pack_kind", "STANDARD", "available", available,
                "champion_guarantee_rarity_id", 4, "champion_guarantee_packs", 10,
                "elect_guarantee_rarity_id", 3, "elect_guarantee_packs", 50);
    }
}
