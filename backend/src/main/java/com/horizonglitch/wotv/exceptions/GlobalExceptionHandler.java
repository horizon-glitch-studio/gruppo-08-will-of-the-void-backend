package com.horizonglitch.wotv.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PackNotFoundException.class)
    public ResponseEntity<Map<String, Object>> packNotFound(PackNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "PACK_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> playerNotFound(PlayerNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "PLAYER_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler(PackUnavailableException.class)
    public ResponseEntity<Map<String, Object>> packUnavailable(PackUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "PACK_NOT_AVAILABLE", "message", exception.getMessage()));
    }

    @ExceptionHandler(InsufficientShardsException.class)
    public ResponseEntity<Map<String, Object>> insufficientShards(InsufficientShardsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "INSUFFICIENT_SHARDS", "message", exception.getMessage(),
                "shardBalance", exception.getShardBalance(), "shardCost", exception.getShardCost()));
    }
}
