package com.syncwatch.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class RezkaServiceRunner {

    @Value("${rezka.service.path:../rezka-service/main.py}")
    private String scriptPath;

    @Value("${rezka.service.python:python3}")
    private String pythonExecutable;

    @Value("${rezka.service.enabled:true}")
    private boolean enabled;

    @Value("${rezka.service.email:}")
    private String email;

    @Value("${rezka.service.password:}")
    private String password;

    private Process process;

    @Bean
    public CommandLineRunner startRezkaService() {
        return args -> {
            if (!enabled) {
                log.info("Rezka service disabled via config, skipping");
                return;
            }

            Path script = Paths.get(scriptPath).toAbsolutePath().normalize();
            if (!script.toFile().exists()) {
                log.warn("Rezka script not found at: {}, skipping", script);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, script.toString())
                    .directory(script.getParent().toFile())
                    .redirectErrorStream(true);

            pb.environment().put("PYTHONUNBUFFERED", "1");

            if (!email.isBlank())    pb.environment().put("REZKA_EMAIL",    email);
            if (!password.isBlank()) pb.environment().put("REZKA_PASSWORD", password);

            process = pb.start();
            log.info("Rezka service started (PID {})", process.pid());

            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    br.lines().forEach(line -> log.info("[rezka] {}", line));
                } catch (Exception ignored) {}
            });
            reader.setDaemon(true);
            reader.setName("rezka-log");
            reader.start();

            Thread watcher = new Thread(() -> {
                try {
                    int code = process.waitFor();
                    if (code != 0) {
                        log.warn("Rezka service exited with code {}", code);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            watcher.setDaemon(true);
            watcher.setName("rezka-watcher");
            watcher.start();
        };
    }

    @PreDestroy
    public void stopRezkaService() {
        if (process != null && process.isAlive()) {
            log.info("Stopping Rezka service (PID {})...", process.pid());
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        }
    }
}
