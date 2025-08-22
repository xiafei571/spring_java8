package com.enterprise.proxy.service;

import com.enterprise.proxy.config.HttpClientConfig;
import com.enterprise.proxy.config.ProxyConfig;
import com.enterprise.proxy.config.TargetConfig;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
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
        
        logger.info("Executing request to: {}", targetUrl);
        logger.info("Using proxy: {}:{}", proxyConfig.getHost(), proxyConfig.getPort());
        
        CloseableHttpClient httpClient = createHttpClientWithNtlmProxy();
        
        try {
            HttpGet request = new HttpGet(targetUrl);
            
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
        
        String[] usernameParts = proxyConfig.getUsername().split("\\\\");
        String domain = usernameParts.length > 1 ? usernameParts[0] : proxyConfig.getDomain();
        String username = usernameParts.length > 1 ? usernameParts[1] : proxyConfig.getUsername();
        
        NTCredentials ntCredentials = new NTCredentials(
                username,
                proxyConfig.getPassword(),
                null, // workstation can be null
                domain
        );
        
        credentialsProvider.setCredentials(
                new AuthScope(proxyConfig.getHost(), proxyConfig.getPort()),
                ntCredentials
        );
        
        RequestConfig requestConfig = RequestConfig.custom()
                .setProxy(proxy)
                .setConnectTimeout(httpClientConfig.getConnection().getTimeout())
                .setSocketTimeout(httpClientConfig.getSocketTimeout())
                .setConnectionRequestTimeout(httpClientConfig.getConnection().getRequestTimeout())
                .build();
        
        return HttpClientBuilder.create()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }
}