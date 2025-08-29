package com.enterprise.proxy;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.*;
import org.apache.http.impl.client.*;
import org.apache.http.message.*;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

public class TokenTest {
    public static void main(String[] args) throws Exception {
        // ====== Fill in your own values here ======
        String certPath = "";        // Path to your client certificate (.pem or .p12)
        String keyPath  = "";        // Path to your private key (.pem) if separate
        String tokenUrl = "";        // Token endpoint
        String scope    = "";        // 
        char[] keystorePassword = "".toCharArray(); // Password for keystore if needed
        // ==========================================

        // Build HttpClient with SSL context (client certificate authentication)
        CloseableHttpClient client = HttpClients.custom()
                .setSSLSocketFactory(buildSSLConnectionSocketFactory(certPath, keyPath, keystorePassword))
                .build();

        // Create POST request
        HttpPost post = new HttpPost(tokenUrl);
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");

        // Add form parameters
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("grant_type", "client_credentials"));
        form.add(new BasicNameValuePair("scope", scope));
        post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));

        // Debug: print request body
        System.out.println(">>> Request body: " + EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8));

        // Execute request
        try (CloseableHttpResponse resp = client.execute(post)) {
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            System.out.println(">>> Response status: " + resp.getStatusLine());
            System.out.println(">>> Response body: " + body);
        }
    }

    /**
     * Build SSLConnectionSocketFactory using client certificate + private key.
     * NOTE: For simplicity this example assumes you already have a PKCS12 keystore.
     * If you have PEM certificate + private key separately, convert them into a .p12 file first.
     */
    private static SSLConnectionSocketFactory buildSSLConnectionSocketFactory(String certPath, String keyPath, char[] password) throws Exception {
        // Load PKCS12 keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(certPath)) {
            ks.load(fis, password);
        }

        // Initialize KeyManagerFactory with client cert
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, password);

        // Initialize TrustManagerFactory (use default trusted CAs)
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init((KeyStore) null);

        // Initialize SSL context
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return new SSLConnectionSocketFactory(
                sslContext,
                new String[]{"TLSv1.2", "TLSv1.3"},
                null,
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()
        );
    }
}
