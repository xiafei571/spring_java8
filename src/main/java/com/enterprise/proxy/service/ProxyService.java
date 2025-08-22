package com.enterprise.proxy.service;

import com.enterprise.proxy.config.HttpClientConfig;
import com.enterprise.proxy.config.ProxyConfig;
import com.enterprise.proxy.config.TargetConfig;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.AuthCache;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.NTLMScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ProxyService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProxyService.class);
    
    private final ProxyConfig proxyConfig;
    private final HttpClientConfig httpClientConfig;
    private final TargetConfig targetConfig;
    
    @Autowired
    public ProxyService(ProxyConfig proxyConfig, HttpClientConfig httpClientConfig, TargetConfig targetConfig) {
        this.proxyConfig = proxyConfig;
        this.httpClientConfig = httpClientConfig;
        this.targetConfig = targetConfig;
    }
    
    public String executeRequest(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            targetUrl = targetConfig.getUrl();
        }
        
        logger.info("=== Proxy Request Execution ===");
        logger.info("Target URL: {}", targetUrl);
        logger.info("Proxy Host: [{}]", proxyConfig.getHost());
        logger.info("Proxy Port: [{}]", proxyConfig.getPort());
        logger.info("Proxy Username from config: [{}]", proxyConfig.getUsername());
        
        // Check if configuration is still default values
        if ("host".equals(proxyConfig.getHost()) || "port".equals(String.valueOf(proxyConfig.getPort()))) {
            logger.error("CONFIGURATION ERROR: You're still using default values!");
            logger.error("Please update your application.properties with real proxy settings");
            return "Error: Configuration not updated from defaults. Please check application.properties";
        }
        
        CloseableHttpClient httpClient = createHttpClientWithNtlmProxy();
        
        try {
            HttpGet request = new HttpGet(targetUrl);
            // Add User-Agent like in your 403 project
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            logger.info("Executing request without preemptive auth - let NTLM handshake happen naturally");
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            logger.info("Response status: {}", statusCode);
            logger.debug("Response body length: {} characters", responseBody.length());
            
            if (statusCode >= 200 && statusCode < 300) {
                logger.info("Request successful!");
                return responseBody;
            } else {
                logger.warn("Request failed with status: {}", statusCode);
                return "Request failed with status: " + statusCode + "\n" + responseBody;
            }
            
        } catch (IOException e) {
            logger.error("Error executing request: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.warn("Error closing HTTP client: {}", e.getMessage());
            }
        }
    }
    
    private CloseableHttpClient createHttpClientWithNtlmProxy() {
        HttpHost proxy = new HttpHost(proxyConfig.getHost(), proxyConfig.getPort());
        
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        
        // Parse username and domain
        String[] usernameParts = proxyConfig.getUsername().split("\\\\");
        String domain = usernameParts.length > 1 ? usernameParts[0] : proxyConfig.getDomain();
        String username = usernameParts.length > 1 ? usernameParts[1] : proxyConfig.getUsername();
        
        logger.info("=== NTLM Authentication Setup ===");
        logger.info("Original username from config: [{}]", proxyConfig.getUsername());
        logger.info("Parsed domain: [{}]", domain);
        logger.info("Parsed username: [{}]", username);
        logger.info("Password length: {}", proxyConfig.getPassword() != null ? proxyConfig.getPassword().length() : 0);
        
        // Validation
        if (username == null || username.trim().isEmpty()) {
            logger.error("Username is empty after parsing! Check your proxy.username configuration");
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (proxyConfig.getPassword() == null || proxyConfig.getPassword().trim().isEmpty()) {
            logger.error("Password is empty! Check your proxy.password configuration");
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (domain == null || domain.trim().isEmpty()) {
            logger.error("Domain is empty! Check your proxy.domain configuration");
            throw new IllegalArgumentException("Domain cannot be empty");
        }
        
        // Get workstation name - exactly like your 403 project
        String workstation = "";
        try {
            workstation = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            workstation = ""; // Use empty string if can't get hostname
        }
        logger.info("Using workstation name: [{}]", workstation);
        
        // Set up NTLM credentials - exactly like your 403 project
        NTCredentials ntCredentials = new NTCredentials(
                username,
                proxyConfig.getPassword(),
                workstation,
                domain != null ? domain : ""
        );
        
        logger.info("NTLM Credentials created - Domain: [{}], Username: [{}], Workstation: [{}]", 
                   domain, username, workstation);
        
        // Add NTLM credentials - exactly like your 403 project
        credentialsProvider.setCredentials(new AuthScope(proxyConfig.getHost(), proxyConfig.getPort()), ntCredentials);
        
        logger.info("Added NTLM credentials to provider for scope: {}:{}", proxyConfig.getHost(), proxyConfig.getPort());
        
        // Configure request with proxy - exactly like your 403 project (no auth preferences)
        RequestConfig requestConfig = RequestConfig.custom()
                .setProxy(proxy)
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .build();
        
        // Create HttpClient with NTLM support - exactly like your 403 project
        return org.apache.http.impl.client.HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }
}