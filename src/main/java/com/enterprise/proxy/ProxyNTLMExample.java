package com.enterprise.proxy;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

public class ProxyNTLMExample {
    public static void main(String[] args) {
        // ====== 需要替换的参数 ======
        String proxyHost = "proxy.example.com"; // 代理服务器地址
        int proxyPort = 8080;                   // 代理端口
        String targetUrl = "https://www.google.com"; // 要访问的目标URL

        String username = "your_username"; // 域账号用户名
        String password = "your_password"; // 密码
        String workstation = "YOUR-PC";    // 本机名，可以随便填
        String domain = "YOUR_DOMAIN";     // 域名

        // ====== 配置 NTLM 凭据 ======
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(proxyHost, proxyPort),
                new NTCredentials(username, password, workstation, domain)
        );

        // ====== 设置代理 + HttpClient ======
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setProxy(proxy)
                .build()) {

            HttpGet request = new HttpGet(targetUrl);
            request.setHeader("User-Agent", "Mozilla/5.0");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                System.out.println("Response Code: " + response.getStatusLine().getStatusCode());
                System.out.println("Response: " + response.getStatusLine());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
