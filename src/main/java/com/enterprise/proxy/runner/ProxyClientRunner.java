package com.enterprise.proxy.runner;

import com.enterprise.proxy.service.ProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ProxyClientRunner implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ProxyClientRunner.class);
    
    private final ProxyService proxyService;
    
    @Autowired
    public ProxyClientRunner(ProxyService proxyService) {
        this.proxyService = proxyService;
    }
    
    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting Proxy Client Application...");
        
        String targetUrl = null;
        
        // Check for command line arguments
        for (int i = 0; i < args.length; i++) {
            if ("--target.url".equals(args[i]) && i + 1 < args.length) {
                targetUrl = args[i + 1];
                logger.info("Using target URL from command line: {}", targetUrl);
                break;
            }
        }
        
        String response = proxyService.executeRequest(targetUrl);
        
        logger.info("=== RESPONSE START ===");
        System.out.println(response);
        logger.info("=== RESPONSE END ===");
        
        logger.info("Proxy Client Application completed successfully.");
    }
}