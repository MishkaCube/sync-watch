package com.syncwatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SyncwatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SyncwatchApplication.class, args);
    }
}
