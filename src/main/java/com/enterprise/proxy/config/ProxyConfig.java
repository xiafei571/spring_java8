package com.enterprise.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "proxy")
public class ProxyConfig {
    
    private String host;
    private int port;
    private String username;
    private String password;
    private String domain;
    
    // BBS alias for Basic authentication
    private String bbsAlias;
    
    // Domain username for NTLM authentication (if different from username)
    private String domainUsername;
    
    public ProxyConfig() {
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getDomain() {
        return domain;
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    public String getBbsAlias() {
        return bbsAlias;
    }
    
    public void setBbsAlias(String bbsAlias) {
        this.bbsAlias = bbsAlias;
    }
    
    public String getDomainUsername() {
        return domainUsername;
    }
    
    public void setDomainUsername(String domainUsername) {
        this.domainUsername = domainUsername;
    }
    
    @Override
    public String toString() {
        return "ProxyConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", domain='" + domain + '\'' +
                ", bbsAlias='" + bbsAlias + '\'' +
                ", domainUsername='" + domainUsername + '\'' +
                '}';
    }
}