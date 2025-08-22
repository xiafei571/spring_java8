# Enterprise Proxy Client

A Spring Boot application that provides NTLM proxy functionality similar to the curl command:
```bash
curl --proxy-ntlm --proxy-user 'TESTPROD\test__dev:password' --proxy 'http://host:port' 'https://www.google.com'
```

## Features

- NTLM proxy authentication
- Command line parameter support
- Configuration file support with override capability
- Enterprise-grade logging
- Maven packaging as executable JAR

## Configuration

### Default Configuration (application.properties)

```properties
# Proxy Configuration
proxy.host=host
proxy.port=port
proxy.username=TESTPROD\\test__dev
proxy.password=password
proxy.domain=TESTPROD

# Target URL Configuration
target.url=https://www.google.com
```

### Command Line Usage

#### Basic Usage
```bash
java -jar target/proxy-client-1.0.0.jar
```

#### Override Configuration via Command Line
```bash
java -jar target/proxy-client-1.0.0.jar \
  --proxy.host=your-proxy-host \
  --proxy.port=8080 \
  --proxy.username="DOMAIN\\username" \
  --proxy.password=yourpassword \
  --target.url=https://www.example.com
```

#### Override Target URL Only
```bash
java -jar target/proxy-client-1.0.0.jar --target.url=https://www.example.com
```

## Build Instructions

### Prerequisites
- Java 8 or higher
- Maven 3.6+

### Build the Project
```bash
mvn clean package
```

### Run the Application
```bash
java -jar target/proxy-client-1.0.0.jar
```

## Project Structure

```
src/main/java/com/enterprise/proxy/
├── ProxyClientApplication.java     # Main application class
├── config/
│   ├── ProxyConfig.java           # Proxy configuration properties
│   ├── HttpClientConfig.java      # HTTP client configuration
│   └── TargetConfig.java          # Target URL configuration
├── service/
│   └── ProxyService.java          # Core proxy service with NTLM auth
└── runner/
    └── ProxyClientRunner.java     # Command line runner
```

## Logging

The application uses SLF4J with Logback for logging. Log levels can be configured in `application.properties`:

```properties
logging.level.com.enterprise.proxy=INFO
logging.level.org.apache.http=DEBUG
```