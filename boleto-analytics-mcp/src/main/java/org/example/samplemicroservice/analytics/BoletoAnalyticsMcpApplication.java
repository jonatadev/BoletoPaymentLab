package org.example.samplemicroservice.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BoletoAnalyticsMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoletoAnalyticsMcpApplication.class, args);
    }
}
