# PowerShell Proxy Client

This PowerShell script provides the same functionality as the Java application but uses PowerShell's native proxy support without requiring credential prompts.

## Files Overview

- `proxy-client.ps1` - Main PowerShell script
- `proxy-config.properties` - Configuration file (fill in your actual proxy settings)
- `run-proxy-client.bat` - Windows batch file for easy execution

## Configuration

1. Edit the `proxy-config.properties` file:
```properties
proxy.url=http://your-proxy-host:port
proxy.username=your_username  
proxy.password=your_password
target.url=https://jsonplaceholder.typicode.com/todos/1
```

**Note**: If `proxy.password` is not set in the configuration file, the script will prompt you to enter the password securely (password input will be hidden).

## Usage

### Method 1: Using Configuration File
```cmd
powershell -ExecutionPolicy Bypass -File scripts\proxy-client.ps1
```

### Method 2: Command Line Parameters
```cmd
powershell -ExecutionPolicy Bypass -File scripts\proxy-client.ps1 -ProxyUrl "http://proxy:8085" -Username "user" -Password "pass"
```

### Method 3: Using Batch File
```cmd
scripts\run-proxy-client.bat
```

### Method 4: Specify Target URL
```cmd
powershell -ExecutionPolicy Bypass -File scripts\proxy-client.ps1 -TargetUrl "https://www.google.com"
```

### Method 5: Secure Password Input
If you don't want to save the password in the configuration file, leave `proxy.password` empty and the script will prompt for input:
```properties
# proxy-config.properties
proxy.url=http://inet-proxy-b.adns.ubs.net:8085
proxy.username=svc_flare_dev
# proxy.password=  (leave empty or remove this line)
```

## Environment Variables (Optional)

You can also set sensitive information through environment variables:
```cmd
set PROXY_PASSWORD=your_password
powershell -ExecutionPolicy Bypass -File scripts\proxy-client.ps1 -Password %PROXY_PASSWORD%
```

## Troubleshooting

If you encounter execution policy errors, you can temporarily allow script execution:
```cmd
powershell -ExecutionPolicy Bypass -File scripts\proxy-client.ps1
```

Or set it permanently (requires administrator privileges):
```cmd
powershell -Command "Set-ExecutionPolicy RemoteSigned -Scope CurrentUser"
```
