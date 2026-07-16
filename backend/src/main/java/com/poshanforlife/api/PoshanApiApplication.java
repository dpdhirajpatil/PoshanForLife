package com.poshanforlife.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PoshanApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PoshanApiApplication.class, args);
    }
}
