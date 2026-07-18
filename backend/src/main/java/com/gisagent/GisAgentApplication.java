package com.gisagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GisAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(GisAgentApplication.class, args);
    }
}
