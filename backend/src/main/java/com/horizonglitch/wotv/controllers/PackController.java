package com.horizonglitch.wotv.controllers;

import com.horizonglitch.wotv.services.PackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/packs")
public class PackController {
    private final PackService packService;

    public PackController(PackService packService) { this.packService = packService; }

    @PostMapping("/{packTypeId}/open")
    public ResponseEntity<PackService.PackOpening> open(@PathVariable int packTypeId, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packService.open(principal.getName(), packTypeId));
    }
}
