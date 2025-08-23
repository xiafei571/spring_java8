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

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

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
        
        // Disable SSL certificate validation for testing (remove in production)
        disableSSLVerification();
    }
    
    private void disableSSLVerification() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) { return true; }
            };
            
            // Set default SSL context and hostname verifier
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            logger.info("SSL certificate verification disabled for testing");
            
        } catch (Exception e) {
            logger.warn("Failed to disable SSL verification: {}", e.getMessage());
        }
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
        logger.info("BBS Alias from config: [{}]", proxyConfig.getBbsAlias());
        logger.info("Domain Username from config: [{}]", proxyConfig.getDomainUsername());
        logger.info("Password length: [{}]", proxyConfig.getPassword() != null ? proxyConfig.getPassword().length() : 0);
        
        // Check if configuration is still default values
        if ("host".equals(proxyConfig.getHost()) || "port".equals(String.valueOf(proxyConfig.getPort()))) {
            logger.error("CONFIGURATION ERROR: You're still using default values!");
            logger.error("Please update your application.properties with real proxy settings");
            return "Error: Configuration not updated from defaults. Please check application.properties";
        }
        
        // Check if we have the required credentials
        if (proxyConfig.getPassword() == null || proxyConfig.getPassword().isEmpty()) {
            logger.error("CONFIGURATION ERROR: Password is not set!");
            return "Error: Password is not configured. Please set proxy.password in application.properties";
        }
        
        // Log password characteristics for debugging (without exposing actual password)
        String password = proxyConfig.getPassword();
        logger.info("Password analysis: length={}, hasSpecialChars={}, hasPipe={}, hasAt={}, hasBackslash={}, hasDot={}", 
                   password.length(), 
                   password.matches(".*[^a-zA-Z0-9].*"),
                   password.contains("|"),
                   password.contains("@"),
                   password.contains("\\"),
                   password.contains("."));
        
        // Check if password needs Properties file unescaping
        String testPassword = password.replace("\\\\", "\\")
                                     .replace("\\:", ":")
                                     .replace("\\=", "=");
        if (!password.equals(testPassword)) {
            logger.info("Password contains Properties file escaping sequences");
        }
        
        // Check for potential encoding issues
        try {
            byte[] passwordBytes = password.getBytes("UTF-8");
            logger.info("Password UTF-8 byte length: {}", passwordBytes.length);
            // Check if byte length differs from character length (indicates multi-byte characters)
            if (passwordBytes.length != password.length()) {
                logger.warn("Password contains multi-byte characters - potential encoding issue");
            }
        } catch (Exception e) {
            logger.warn("Could not analyze password encoding: {}", e.getMessage());
        }
        
        // Try Kerberos first (like PowerShell), then NTLM, then Basic
        String result = executeRequestWithKerberos(targetUrl);
        if (result.contains("407 Proxy Authentication Error")) {
            logger.warn("Kerberos failed, trying NTLM...");
            result = executeRequestWithNtlm(targetUrl);
            if (result.contains("407 Proxy Authentication Error")) {
                logger.warn("NTLM failed, trying Basic...");
                result = executeRequestWithBasic(targetUrl);
            }
        }
        
        return result;
    }
    
    private String executeRequestWithNtlm(String targetUrl) {
        boolean enableNegotiate = Boolean.parseBoolean(System.getProperty("proxy.enable.negotiate", "false"));
        if (enableNegotiate) {
            // Try SPNEGO with configured credentials first (no interactive prompt)
            String spnegoResult = trySpnegoWithSuppliedCredentials(targetUrl);
            if (spnegoResult != null) {
                return spnegoResult;
            }
        }
        
        CloseableHttpClient httpClient = createHttpClientWithNtlmProxy();
        
        try {
            HttpGet request = new HttpGet(targetUrl);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            logger.info("Executing request with NTLM authentication");
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            
            logger.info("NTLM Response status: {}", statusCode);
            
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

    private String trySpnegoWithSuppliedCredentials(String targetUrl) {
        try {
            String user = proxyConfig.getUsername();
            String pass = proxyConfig.getPassword();
            if (user == null || user.isEmpty() || pass == null) {
                logger.info("SPNEGO: no configured credentials found, skipping supplied-cred attempt");
                return null;
            }
            // For DOMAIN\\user, JAAS expects user@REALM or just user; here we pass as is via callback
            LoginContext lc = new LoginContext("spnego", null, new SimpleCredCallback(user, pass), new SpnegoLoginConfig());
            lc.login();
            Subject subject = lc.getSubject();
            logger.info("SPNEGO: obtained Subject via configured credentials; attempting HTTP under Subject.doAs");
            
            return Subject.doAs(subject, (PrivilegedAction<String>) () -> {
                CloseableHttpClient client = createHttpClientForNegotiateProxy();
                try {
                    HttpGet req = new HttpGet(targetUrl);
                    req.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    HttpResponse resp = client.execute(req);
                    int sc = resp.getStatusLine().getStatusCode();
                    if (sc == 407) {
                        logger.error("SPNEGO supplied-cred attempt got 407; {}", minimalAuthInfo(resp));
                        consumeQuietly(resp.getEntity());
                        return "407 Proxy Authentication Error. Check logs for details.";
                    }
                    if (sc >= 200 && sc < 300) {
                        try {
                            return EntityUtils.toString(resp.getEntity());
                        } catch (IOException e) {
                            return "Error: " + e.getMessage();
                        }
                    }
                    String msg = minimalFailureMessage(resp, sc);
                    consumeQuietly(resp.getEntity());
                    return msg;
                } catch (IOException e) {
                    return "Error: " + e.getMessage();
                } finally {
                    try { client.close(); } catch (IOException ignore) {}
                }
            });
        } catch (LoginException e) {
            logger.warn("SPNEGO: login with configured credentials failed: {}", e.getMessage());
            return null; // fall back to normal path
        }
    }

    private static class SimpleCredCallback implements CallbackHandler {
        private final String username;
        private final String password;
        SimpleCredCallback(String username, String password) {
            this.username = username;
            this.password = password;
        }
        @Override
        public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback) {
                    ((NameCallback) cb).setName(username);
                } else if (cb instanceof PasswordCallback) {
                    ((PasswordCallback) cb).setPassword(password.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(cb);
                }
            }
        }
    }

    private static class SpnegoLoginConfig extends Configuration {
        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            Map<String, String> options = new HashMap<>();
            options.put("useTicketCache", "false");
            options.put("storeKey", "false");
            options.put("doNotPrompt", "true");
            options.put("refreshKrb5Config", "true");
            options.put("isInitiator", "true");
            // Allow username/password from CallbackHandler
            options.put("useKeyTab", "false");
            options.put("tryFirstPass", "true");
            options.put("useFirstPass", "true");
            // On Windows, this still goes through SSPI
            return new AppConfigurationEntry[]{
                    new AppConfigurationEntry(
                            "com.sun.security.auth.module.Krb5LoginModule",
                            LoginModuleControlFlag.REQUIRED,
                            options
                    )
            };
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
            
            // Allow overriding Basic username with BBS alias via system property
            String basicUserOverride = System.getProperty("proxy.basic.username");
            String effectiveUser = basicUserOverride != null && !basicUserOverride.isEmpty()
                    ? basicUserOverride
                    : proxyConfig.getUsername();
            if (basicUserOverride != null) {
                logger.info("Using Basic username override (BBS alias): [{}]", effectiveUser);
            }
            
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
        String username = proxyConfig.getDomainUsername() != null ? proxyConfig.getDomainUsername() : proxyConfig.getUsername();
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
        String username = proxyConfig.getBbsAlias() != null ? proxyConfig.getBbsAlias() : proxyConfig.getUsername();
        String password = proxyConfig.getPassword();

        logger.info("=== Basic Authentication Setup ===");
        logger.info("Using BBS alias username [{}]", username);

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

    private CloseableHttpClient createHttpClientForNegotiateProxy() {
        String proxyHost = proxyConfig.getHost();
        int proxyPort = proxyConfig.getPort();
        
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        
        RequestConfig config = RequestConfig.custom()
                .setProxy(proxy)
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.SPNEGO, AuthSchemes.NTLM, AuthSchemes.BASIC))
                .setAuthenticationEnabled(true)
                .build();
        
        Registry<AuthSchemeProvider> authRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true))
                .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .build();
        
        HttpClientBuilder builder;
        if (WinHttpClients.isWinAuthAvailable()) {
            logger.info("Using Windows native SSPI for Negotiate/NTLM (WinHttpClients)");
            builder = WinHttpClients.custom();
        } else {
            logger.info("Windows SSPI not available, using standard HttpClient");
            builder = HttpClientBuilder.create();
        }
        
        return builder
                .setDefaultAuthSchemeRegistry(authRegistry)
                .setDefaultRequestConfig(config)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .build();
    }

    private CloseableHttpClient createHttpClientForKerberosProxy() {
        String proxyHost = proxyConfig.getHost();
        int proxyPort = proxyConfig.getPort();
        
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        
        RequestConfig config = RequestConfig.custom()
                .setProxy(proxy)
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.SPNEGO, AuthSchemes.NTLM, AuthSchemes.BASIC))
                .setAuthenticationEnabled(true)
                .build();
        
        Registry<AuthSchemeProvider> authRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true))
                .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .build();
        
        HttpClientBuilder builder;
        if (WinHttpClients.isWinAuthAvailable()) {
            logger.info("Using Windows native SSPI for Kerberos/NTLM (WinHttpClients)");
            builder = WinHttpClients.custom();
        } else {
            logger.info("Windows SSPI not available, using standard HttpClient");
            builder = HttpClientBuilder.create();
        }
        
        return builder
                .setDefaultAuthSchemeRegistry(authRegistry)
                .setDefaultRequestConfig(config)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .build();
    }

    private String executeRequestWithKerberos(String targetUrl) {
        CloseableHttpClient httpClient = createHttpClientForKerberosProxy();
        
        try {
            HttpGet request = new HttpGet(targetUrl);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            logger.info("Executing request with Kerberos (Negotiate) authentication");
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            
            logger.info("Kerberos Response status: {}", statusCode);
            
            if (statusCode == 407) {
                logger.error("=== 407 PROXY AUTHENTICATION ERROR (Kerberos) ===");
                logger.error(minimalAuthInfo(response));
                consumeQuietly(response.getEntity());
                return "407 Proxy Authentication Error. Check logs for details.";
            }
            
            if (statusCode >= 200 && statusCode < 300) {
                String responseBody = EntityUtils.toString(response.getEntity());
                logger.debug("Response body length: {} characters", responseBody.length());
                logger.info("Kerberos authentication successful!");
                return responseBody;
            } else {
                String msg = minimalFailureMessage(response, statusCode);
                consumeQuietly(response.getEntity());
                logger.warn("Kerberos request failed: {}", msg);
                return msg;
            }
            
        } catch (IOException e) {
            logger.error("Error executing Kerberos request: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.warn("Error closing HTTP client: {}", e.getMessage());
            }
        }
    }
}