package com.enterprise.proxy.service;

import com.enterprise.proxy.config.HttpClientConfig;
import com.enterprise.proxy.config.ProxyConfig;
import com.enterprise.proxy.config.TargetConfig;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.client.WinHttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

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
        
        // Try NTLM first (with configured domain)
        String result = executeRequestWithNtlm(targetUrl);
        if (result.contains("407 Proxy Authentication Error")) {
            // Retry NTLM with empty domain/workstation (some proxies expect default domain)
            logger.warn("NTLM with configured domain failed. Retrying NTLM with EMPTY domain/workstation...");
            String retry = executeRequestWithNtlmEmptyDomain(targetUrl);
            if (!retry.contains("407 Proxy Authentication Error")) {
                return retry;
            }
            logger.warn("NTLM with empty domain also failed, trying Basic authentication...");
            result = executeRequestWithBasic(targetUrl);
        }
        
        return result;
    }
    
    private String executeRequestWithNtlm(String targetUrl) {
        CloseableHttpClient httpClient = createHttpClientWithNtlmProxy();
        
        try {
            HttpGet request = new HttpGet(targetUrl);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            // No preemptive NTLM (not supported). Let handshake proceed after 407.
            logger.info("Executing request with NTLM authentication (no preemptive cache)");
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            
            logger.info("NTLM Response status: {}", statusCode);
            
            // Handle 407 authentication error specifically
            if (statusCode == 407) {
                logger.error("=== 407 PROXY AUTHENTICATION ERROR (NTLM) ===");
                logger.error(minimalAuthInfo(response));
                consumeQuietly(response.getEntity());
                return "407 Proxy Authentication Error. Check logs for details.";
            }
            
            if (statusCode >= 200 && statusCode < 300) {
                String responseBody = EntityUtils.toString(response.getEntity());
                logger.debug("Response body length: {} characters", responseBody.length());
                logger.info("NTLM authentication successful!");
                return responseBody;
            } else {
                String msg = minimalFailureMessage(response, statusCode);
                consumeQuietly(response.getEntity());
                logger.warn("NTLM request failed: {}", msg);
                return msg;
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
    
    private String executeRequestWithNtlmEmptyDomain(String targetUrl) {
        CloseableHttpClient httpClient = createHttpClientWithNtlmProxyUsing("", "");
        
        try {
            HttpGet request = new HttpGet(targetUrl);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            logger.info("Executing request with NTLM (EMPTY domain, EMPTY workstation)");
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            
            logger.info("NTLM(empty domain) Response status: {}", statusCode);
            
            if (statusCode == 407) {
                logger.error("=== 407 PROXY AUTHENTICATION ERROR (NTLM empty domain) ===");
                logger.error(minimalAuthInfo(response));
                consumeQuietly(response.getEntity());
                return "407 Proxy Authentication Error. Check logs for details.";
            }
            
            if (statusCode >= 200 && statusCode < 300) {
                String responseBody = EntityUtils.toString(response.getEntity());
                logger.debug("Response body length: {} characters", responseBody.length());
                logger.info("NTLM (empty domain) authentication successful!");
                return responseBody;
            }
            String msg = minimalFailureMessage(response, statusCode);
            consumeQuietly(response.getEntity());
            return msg;
        } catch (IOException e) {
            logger.error("Error executing NTLM(empty domain) request: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        } finally {
            try { httpClient.close(); } catch (IOException ignore) {}
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
                logger.error(minimalAuthInfo(response));
                consumeQuietly(response.getEntity());
                return "407 Proxy Authentication Error - Both NTLM and Basic failed. Check logs for details.";
            }
            
            if (statusCode >= 200 && statusCode < 300) {
                String responseBody = EntityUtils.toString(response.getEntity());
                logger.debug("Response body length: {} characters", responseBody.length());
                logger.info("Basic authentication successful!");
                return responseBody;
            } else {
                String msg = minimalFailureMessage(response, statusCode);
                consumeQuietly(response.getEntity());
                logger.warn("Basic request failed: {}", msg);
                return msg;
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

    private String minimalAuthInfo(HttpResponse response) {
        String proxySchemes = Arrays.stream(response.getHeaders("Proxy-Authenticate"))
                .map(h -> h.getValue())
                .collect(Collectors.joining(", "));
        return "Proxy-Authenticate: [" + proxySchemes + "]";
    }

    private String minimalFailureMessage(HttpResponse response, int statusCode) {
        String proxySchemes = Arrays.stream(response.getHeaders("Proxy-Authenticate"))
                .map(h -> h.getValue())
                .collect(Collectors.joining(", "));
        return "Request failed with status: " + statusCode + (proxySchemes.isEmpty() ? "" : "; Proxy-Authenticate: [" + proxySchemes + "]");
    }

    private void consumeQuietly(HttpEntity entity) {
        try {
            if (entity != null) {
                EntityUtils.consume(entity);
            }
        } catch (Exception ignore) {}
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
        
        return createHttpClientWithNtlmProxyUsing(actualDomain, workstation);
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
                .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
                .setAuthenticationEnabled(true)
                .build();

        // Create HttpClient with Basic support and authentication strategy
        return HttpClientBuilder.create()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(config)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .build();
    }

    private CloseableHttpClient createHttpClientWithNtlmProxyUsing(String domain, String workstation) {
        String proxyHost = proxyConfig.getHost();
        int proxyPort = proxyConfig.getPort();
        String username = proxyConfig.getUsername();
        String password = proxyConfig.getPassword();
        
        // If username contains DOMAIN\\user, strip domain part because domain is passed separately
        String actualUsername = username;
        if (username != null && username.contains("\\")) {
            String[] parts = username.split("\\\\", 2);
            if (parts.length == 2) {
                actualUsername = parts[1];
            }
        }
        
        // Set up proxy host
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        
        // Create NTLM credentials
        NTCredentials ntCredentials = new NTCredentials(
                actualUsername,
                password,
                workstation == null ? "" : workstation,
                domain == null ? "" : domain
        );
        
        logger.info("NTLM Credentials created - Domain: [{}], Username: [{}], Workstation: [{}]",
                domain == null ? "" : domain,
                actualUsername,
                workstation == null ? "" : workstation);
        
        // Set up credentials provider with proper AuthScope
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), ntCredentials);
        
        // Prefer NTLM then Basic by default; Negotiate can be enabled if required
        RequestConfig config = RequestConfig.custom()
                .setProxy(proxy)
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.BASIC))
                .setAuthenticationEnabled(true)
                .build();
        
        // Build auth scheme registry: NTLM and Basic always; optionally Negotiate
        RegistryBuilder<AuthSchemeProvider> regBuilder = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                .register(AuthSchemes.BASIC, new BasicSchemeFactory());
        boolean enableNegotiate = Boolean.parseBoolean(System.getProperty("proxy.enable.negotiate", "false"));
        if (enableNegotiate) {
            logger.info("Negotiate (SPNEGO) enabled via -Dproxy.enable.negotiate=true");
            regBuilder.register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true));
        } else {
            logger.info("Negotiate (SPNEGO) disabled");
        }
        Registry<AuthSchemeProvider> authRegistry = regBuilder.build();
        
        HttpClientBuilder builder;
        if (WinHttpClients.isWinAuthAvailable()) {
            logger.info("Using Windows native SSPI for NTLM (WinHttpClients)");
            builder = WinHttpClients.custom();
        } else {
            logger.info("Windows SSPI not available, using standard HttpClient");
            builder = HttpClientBuilder.create();
        }
        
        return builder
                .setDefaultAuthSchemeRegistry(authRegistry)
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(config)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .build();
    }
}