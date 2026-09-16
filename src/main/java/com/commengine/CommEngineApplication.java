package com.commengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CommEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommEngineApplication.class, args);
    }
}
