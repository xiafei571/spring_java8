package com.enterprise.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "target")
public class TargetConfig {
    
    private String url = "https://www.google.com";
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    @Override
    public String toString() {
        return "TargetConfig{" +
                "url='" + url + '\'' +
                '}';
    }
}