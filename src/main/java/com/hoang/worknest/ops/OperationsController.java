package com.hoang.worknest.ops;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OperationsController {
    private final ReadinessService readinessService;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        boolean ready = readinessService.isReady();
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("status", ready ? "UP" : "DOWN"));
    }
}
