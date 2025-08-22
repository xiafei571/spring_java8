package com.enterprise.proxy.service;

import com.enterprise.proxy.config.HttpClientConfig;
import com.enterprise.proxy.config.ProxyConfig;
import com.enterprise.proxy.config.TargetConfig;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.NTLMScheme;
import org.apache.http.impl.client.BasicAuthCache;
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
        
        // Try NTLM first
        String result = executeRequestWithNtlm(targetUrl);
        if (result.contains("407 Proxy Authentication Error")) {
            logger.warn("NTLM authentication failed, trying Basic authentication...");
            result = executeRequestWithBasic(targetUrl);
        }
        
        return result;
    }
    
    private String executeRequestWithNtlm(String targetUrl) {
        CloseableHttpClient httpClient = createHttpClientWithNtlmProxy();
        
        try {
            HttpGet request = new HttpGet(targetUrl);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            // Create context with auth cache for NTLM
            HttpClientContext context = HttpClientContext.create();
            setupAuthCache(context);
            
            logger.info("Executing request with NTLM authentication and auth cache");
            HttpResponse response = httpClient.execute(request, context);
            int statusCode = response.getStatusLine().getStatusCode();
            
            logger.info("NTLM Response status: {}", statusCode);
            
            // Handle 407 authentication error specifically
            if (statusCode == 407) {
                logger.error("=== 407 PROXY AUTHENTICATION ERROR (NTLM) ===");
                logger.error("NTLM authentication failed. Trying Basic authentication next...");
                
                // Log response headers for debugging
                logger.error("Response headers:");
                for (org.apache.http.Header header : response.getAllHeaders()) {
                    logger.error("  {}: {}", header.getName(), header.getValue());
                }
                
                String responseBody = EntityUtils.toString(response.getEntity());
                logger.error("Response body: {}", responseBody);
                
                return "407 Proxy Authentication Error. Check logs for details.";
            }
            
            String responseBody = EntityUtils.toString(response.getEntity());
            logger.debug("Response body length: {} characters", responseBody.length());
            
            if (statusCode >= 200 && statusCode < 300) {
                logger.info("NTLM authentication successful!");
                return responseBody;
            } else {
                logger.warn("NTLM request failed with status: {}", statusCode);
                return "Request failed with status: " + statusCode + "\n" + responseBody;
            }
            
        } catch (IOException e) {
            logger.error("Error executing NTLM request: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.warn("Error closing HTTP client: {}", e.getMessage());
            }
        }
    }
    
    private String executeRequestWithBasic(String targetUrl) {
        CloseableHttpClient httpClient = createHttpClientWithBasicProxy();
        
        try {
            HttpGet request = new HttpGet(targetUrl);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            logger.info("Executing request with Basic authentication");
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            
            logger.info("Basic Response status: {}", statusCode);
            
            if (statusCode == 407) {
                logger.error("=== 407 PROXY AUTHENTICATION ERROR (Basic) ===");
                logger.error("Both NTLM and Basic authentication failed!");
                logger.error("Please check your credentials and proxy configuration.");
                
                // Log response headers for debugging
                logger.error("Response headers:");
                for (org.apache.http.Header header : response.getAllHeaders()) {
                    logger.error("  {}: {}", header.getName(), header.getValue());
                }
                
                String responseBody = EntityUtils.toString(response.getEntity());
                logger.error("Response body: {}", responseBody);
                
                return "407 Proxy Authentication Error - Both NTLM and Basic failed. Check logs for details.";
            }
            
            String responseBody = EntityUtils.toString(response.getEntity());
            logger.debug("Response body length: {} characters", responseBody.length());
            
            if (statusCode >= 200 && statusCode < 300) {
                logger.info("Basic authentication successful!");
                return responseBody;
            } else {
                logger.warn("Basic request failed with status: {}", statusCode);
                return "Request failed with status: " + statusCode + "\n" + responseBody;
            }
            
        } catch (IOException e) {
            logger.error("Error executing Basic request: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.warn("Error closing HTTP client: {}", e.getMessage());
            }
        }
    }
    
    private void setupAuthCache(HttpClientContext context) {
        String proxyHost = proxyConfig.getHost();
        int proxyPort = proxyConfig.getPort();
        
        // Create auth cache and preemptively authenticate against proxy
        AuthCache authCache = new BasicAuthCache();
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        authCache.put(proxy, new NTLMScheme());
        context.setAuthCache(authCache);
        
        logger.info("Auth cache configured for proxy: {}:{}", proxyHost, proxyPort);
    }
    
    private CloseableHttpClient createHttpClientWithNtlmProxy() {
        String proxyHost = proxyConfig.getHost();
        int proxyPort = proxyConfig.getPort();
        String username = proxyConfig.getUsername();
        String password = proxyConfig.getPassword();
        String domain = proxyConfig.getDomain();
        
        logger.info("=== NTLM Authentication Setup ===");
        logger.info("Using username [{}] with domain [{}]", username, domain);
        
        // Parse username and domain
        String actualUsername = username;
        String actualDomain = domain != null ? domain : "";
        
        if (username != null && username.contains("\\")) {
            String[] parts = username.split("\\\\", 2);
            if (parts.length == 2) {
                actualDomain = parts[0];
                actualUsername = parts[1];
                logger.info("Extracted domain [{}] and username [{}] from [{}]", actualDomain, actualUsername, username);
            }
        }
        
        logger.info("Parsed domain: [{}]", actualDomain);
        logger.info("Parsed username: [{}]", actualUsername);
        logger.info("Password length: {}", password != null ? password.length() : 0);
        
        // Get workstation name
        String workstation = "";
        try {
            workstation = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            workstation = "";
        }
        logger.info("Using workstation name: [{}]", workstation);
        
        // Set up proxy host
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        
        // Create NTLM credentials
        NTCredentials ntCredentials = new NTCredentials(
                actualUsername, 
                password, 
                workstation, 
                actualDomain
        );
        
        logger.info("NTLM Credentials created - Domain: [{}], Username: [{}], Workstation: [{}]", 
                   actualDomain, actualUsername, workstation);
        
        // Set up credentials provider with proper AuthScope
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), ntCredentials);
        
        // Configure request with proxy and NTLM authentication
        RequestConfig config = RequestConfig.custom()
                .setProxy(proxy)
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .setAuthenticationEnabled(true)
                .build();
        
        // Create HttpClient with NTLM support and authentication strategy
        return HttpClientBuilder.create()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(config)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .build();
    }

    private CloseableHttpClient createHttpClientWithBasicProxy() {
        String proxyHost = proxyConfig.getHost();
        int proxyPort = proxyConfig.getPort();
        String username = proxyConfig.getUsername();
        String password = proxyConfig.getPassword();

        logger.info("=== Basic Authentication Setup ===");
        logger.info("Using username [{}]", username);

        // Set up proxy host
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);

        // Create Basic credentials
        UsernamePasswordCredentials basicCredentials = new UsernamePasswordCredentials(username, password);

        logger.info("Basic Credentials created - Username: [{}]", username);

        // Set up credentials provider with proper AuthScope
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), basicCredentials);

        // Configure request with proxy and Basic authentication
        RequestConfig config = RequestConfig.custom()
                .setProxy(proxy)
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .setAuthenticationEnabled(true)
                .build();

        // Create HttpClient with Basic support and authentication strategy
        return HttpClientBuilder.create()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(config)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .build();
    }
}