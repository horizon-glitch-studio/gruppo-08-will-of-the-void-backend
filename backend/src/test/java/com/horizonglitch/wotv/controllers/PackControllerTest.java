package com.horizonglitch.wotv.controllers;

import com.horizonglitch.wotv.services.PackService;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PackControllerTest {
    @Test
    void opensPackForAuthenticatedPrincipalAndReturnsCreated() {
        var service = mock(PackService.class);
        var controller = new PackController(service);
        var opening = new PackService.PackOpening(8, 2, "Evento", 300, 700,
                Instant.parse("2026-11-16T12:00:00Z"), List.of());
        when(service.open("summoner", 2)).thenReturn(opening);
        Principal principal = () -> "summoner";

        var response = controller.open(2, principal);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(opening, response.getBody());
        verify(service).open("summoner", 2);
    }
}
