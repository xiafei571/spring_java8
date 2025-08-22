package com.enterprise.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "http.client")
public class HttpClientConfig {
    
    private Connection connection = new Connection();
    private int socketTimeout = 30000;
    
    public Connection getConnection() {
        return connection;
    }
    
    public void setConnection(Connection connection) {
        this.connection = connection;
    }
    
    public int getSocketTimeout() {
        return socketTimeout;
    }
    
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
    
    public static class Connection {
        private int timeout = 10000;
        private int requestTimeout = 5000;
        
        public int getTimeout() {
            return timeout;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
        
        public int getRequestTimeout() {
            return requestTimeout;
        }
        
        public void setRequestTimeout(int requestTimeout) {
            this.requestTimeout = requestTimeout;
        }
    }
}