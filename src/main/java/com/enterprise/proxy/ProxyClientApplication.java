package com.enterprise.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ProxyClientApplication {
    
    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        SpringApplication.run(ProxyClientApplication.class, args);
    }
}