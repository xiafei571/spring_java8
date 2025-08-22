package com.enterprise.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ProxyClientApplication {
    
    public static void main(String[] args) {
        // CRITICAL: Disable SOCKS before ANYTHING else
        System.setProperty("java.net.useSystemProxies", "false");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        System.clearProperty("socksProxyVersion");
        System.clearProperty("socksNonProxyHosts");
        
        // Force Basic authentication only - disable all integrated auth
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
        System.clearProperty("java.security.krb5.conf");
        System.clearProperty("java.security.auth.login.config");
        System.setProperty("sun.security.spnego.debug", "false");
        
        // Network settings
        System.setProperty("networkaddress.cache.ttl", "0");
        System.setProperty("networkaddress.cache.negative.ttl", "0");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        
        System.setProperty("spring.main.web-application-type", "none");
        SpringApplication.run(ProxyClientApplication.class, args);
    }
}