package com.hdfcbank.standin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DaprAgentWorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(DaprAgentWorkflowApplication.class, args);
    }
}