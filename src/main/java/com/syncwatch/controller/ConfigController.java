package com.syncwatch.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes runtime feature flags to the frontend so the UI can adapt
 * (e.g. hide the HDRezka tab when the feature is disabled).
 */
@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "http://localhost:5173")
public class ConfigController {

    @Value("${rezka.feature.enabled:true}")
    private boolean rezkaEnabled;

    @GetMapping
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(Map.of(
                "rezkaEnabled", rezkaEnabled
        ));
    }
}
