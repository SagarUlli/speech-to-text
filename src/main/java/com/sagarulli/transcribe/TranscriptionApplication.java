package com.sagarulli.transcribe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TranscriptionApplication {
    public static void main(String[] args) {
        SpringApplication.run(TranscriptionApplication.class, args);
    }
}
