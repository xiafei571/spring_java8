package com.enterprise.proxy;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;


public class HttpsViaNtlmProxyDemo {
    
    public static void main(String[] args) throws Exception {
        // ---- 1) Initialize parameters (modify to read from args/env as needed) ----
        final String proxyHost   = "YOUR_PROXY_HOST";
        final int    proxyPort   = 8080;
        final String targetUrl   = "https://www.google.com";

        final String username    = "yourUser";     // Username only, without domain
        final String password    = "yourPass";
        final String workstation = "YOUR-PC";      // Local machine name (can be empty string)
        final String domain      = "YOURDOMAIN";   // Domain name (AD domain)

        // ---- 2) Load Windows root certificates (Windows-ROOT), trust company proxy's man-in-the-middle certificates ----
        SSLContext sslContext = buildSslContextFromWindowsRoot();

        // Enable only TLSv1.2 (Java 8's common safest option)
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext,
                new String[] {"TLSv1.2"},
                null,
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()
        );

        // ---- 3) NTLM proxy authentication ----
        BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(
                new AuthScope(proxyHost, proxyPort),
                new NTCredentials(username, password, workstation, domain)
        );

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(15_000)
                .setSocketTimeout(30_000)
                .setConnectionRequestTimeout(15_000)
                .setProxy(proxy)
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCredentialsProvider(creds)
                // This is crucial: enable client support for proxy 407 authentication flow
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .setSSLSocketFactory(sslSocketFactory)
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpGet get = new HttpGet(targetUrl);
            // Some enterprise gateways are sensitive to User-Agent, set a common UA for better compatibility
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            get.setHeader("Accept", "*/*");
            get.setHeader("Connection", "close");

            try (CloseableHttpResponse resp = client.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                String bodyPreview = resp.getEntity() != null
                        ? EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8)
                        : "";
                System.out.println("HTTP Status: " + status);
                System.out.println("Body (first 500 chars):");
                System.out.println(bodyPreview.substring(0, Math.min(500, bodyPreview.length())));
            }
        }
    }

    // Build SSLContext from Windows trusted root certificate store
    private static SSLContext buildSslContextFromWindowsRoot() throws Exception {
        // On Windows, SunMSCAPI provides "Windows-ROOT" KeyStore
        KeyStore windowsRoot = KeyStore.getInstance("Windows-ROOT");
        windowsRoot.load(null, null);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(windowsRoot);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }
}
