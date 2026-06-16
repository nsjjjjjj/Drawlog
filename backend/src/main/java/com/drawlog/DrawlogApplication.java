package com.drawlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DrawlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(DrawlogApplication.class, args);
    }
}
