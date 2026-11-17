package com.horizonglitch.wotv.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsMissingPackToNotFound() {
        var response = handler.packNotFound(new PackNotFoundException(12));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("PACK_NOT_FOUND", response.getBody().get("code"));
    }

    @Test
    void mapsMissingPlayerToNotFound() {
        var response = handler.playerNotFound(new PlayerNotFoundException());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("PLAYER_NOT_FOUND", response.getBody().get("code"));
    }

    @Test
    void mapsUnavailablePackToConflict() {
        var response = handler.packUnavailable(new PackUnavailableException(12));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PACK_NOT_AVAILABLE", response.getBody().get("code"));
    }

    @Test
    void returnsBalanceAndCostForInsufficientShards() {
        var response = handler.insufficientShards(new InsufficientShardsException(120, 300));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INSUFFICIENT_SHARDS", response.getBody().get("code"));
        assertEquals(120, response.getBody().get("shardBalance"));
        assertEquals(300, response.getBody().get("shardCost"));
    }
}
