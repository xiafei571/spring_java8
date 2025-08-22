# Enterprise Proxy Client PowerShell Script
# This script provides the same functionality as the Java application but uses PowerShell's native proxy support

param(
    [string]$TargetUrl = "https://jsonplaceholder.typicode.com/todos/1",
    [string]$ProxyUrl = "",
    [string]$Username = "",
    [string]$Password = "",
    [string]$ConfigFile = "proxy-config.properties"
)

# Function to read configuration from properties file
function Read-ProxyConfig {
    param([string]$ConfigPath)
    
    $config = @{}
    if (Test-Path $ConfigPath) {
        Write-Host "Reading configuration from: $ConfigPath"
        Get-Content $ConfigPath | ForEach-Object {
            if ($_ -match '^([^#=]+)=(.*)$') {
                $key = $matches[1].Trim()
                $value = $matches[2].Trim()
                $config[$key] = $value
            }
        }
    } else {
        Write-Warning "Configuration file not found: $ConfigPath"
        Write-Host "Please create the configuration file or provide parameters directly."
    }
    return $config
}

# Function to execute proxy request
function Invoke-ProxyRequest {
    param(
        [string]$Url,
        [string]$Proxy,
        [string]$User,
        [string]$Pass
    )
    
    try {
        Write-Host "=== Proxy Request Execution ===" -ForegroundColor Green
        Write-Host "Target URL: $Url"
        Write-Host "Proxy: $Proxy"
        Write-Host "Username: $User"
        Write-Host "Password length: $($Pass.Length)" 
        Write-Host ""
        
        # Create credential object
        $securePassword = ConvertTo-SecureString $Pass -AsPlainText -Force
        $credential = New-Object System.Management.Automation.PSCredential($User, $securePassword)
        
        Write-Host "Executing request with proxy authentication..." -ForegroundColor Yellow
        
        # Execute the request (without -UseBasicParsing to match your working command)
        $response = Invoke-WebRequest -Uri $Url -Proxy $Proxy -ProxyCredential $credential
        
        Write-Host "Response Status: $($response.StatusCode)" -ForegroundColor Green
        Write-Host "Response Headers:"
        $response.Headers.GetEnumerator() | ForEach-Object {
            Write-Host "  $($_.Key): $($_.Value -join ', ')"
        }
        Write-Host ""
        Write-Host "=== RESPONSE START ===" -ForegroundColor Cyan
        Write-Host $response.Content
        Write-Host "=== RESPONSE END ===" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Proxy request completed successfully!" -ForegroundColor Green
        
        return $true
        
    } catch {
        Write-Host "=== ERROR ===" -ForegroundColor Red
        Write-Host "Request failed: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Exception Type: $($_.Exception.GetType().Name)" -ForegroundColor Red
        
        if ($_.Exception.Response) {
            Write-Host "HTTP Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
            Write-Host "Status Description: $($_.Exception.Response.StatusDescription)" -ForegroundColor Red
        }
        
        return $false
    }
}

# Main execution
Write-Host "Enterprise Proxy Client (PowerShell)" -ForegroundColor Magenta
Write-Host "=====================================" -ForegroundColor Magenta
Write-Host ""

# Read configuration if parameters not provided
if (-not $ProxyUrl -or -not $Username -or -not $Password) {
    $config = Read-ProxyConfig -ConfigPath $ConfigFile
    
    if (-not $ProxyUrl -and $config["proxy.url"]) { $ProxyUrl = $config["proxy.url"] }
    if (-not $Username -and $config["proxy.username"]) { $Username = $config["proxy.username"] }
    if (-not $Password -and $config["proxy.password"]) { $Password = $config["proxy.password"] }
    if (-not $TargetUrl -and $config["target.url"]) { $TargetUrl = $config["target.url"] }
}

# Validate required parameters
if (-not $ProxyUrl) {
    Write-Host "Error: Proxy URL is required. Set proxy.url in config file or use -ProxyUrl parameter." -ForegroundColor Red
    exit 1
}

if (-not $Username) {
    Write-Host "Error: Username is required. Set proxy.username in config file or use -Username parameter." -ForegroundColor Red
    exit 1
}

# Handle password - prompt if not provided in config or parameter
if (-not $Password) {
    Write-Host "Password not found in configuration file." -ForegroundColor Yellow
    Write-Host "Please enter password for user: $Username" -ForegroundColor Yellow
    
    # Prompt for password securely (hidden input)
    $securePasswordInput = Read-Host "Password" -AsSecureString
    
    # Convert secure string to plain text for use in credential
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePasswordInput)
    $Password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($BSTR)
    
    if (-not $Password) {
        Write-Host "Error: Password cannot be empty." -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Password entered successfully." -ForegroundColor Green
}

# Execute the proxy request
$success = Invoke-ProxyRequest -Url $TargetUrl -Proxy $ProxyUrl -User $Username -Pass $Password

if ($success) {
    exit 0
} else {
    exit 1
}
