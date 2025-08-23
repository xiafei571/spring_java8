# Minimal PowerShell Proxy Test - Exactly matches manual command
# This script eliminates all potential differences with manual execution

param(
    [string]$ProxyUrl = "",
    [string]$Username = "", 
    [string]$password = "", 
    [string]$TargetUrl = "https://jsonplaceholder.typicode.com/todos/1"
)

# Simple password prompt - exactly like manual entry
if (-not $password) {
    $securePassword = Read-Host "Enter password" -AsSecureString
} else {
    # Convert plain text password to SecureString (like your manual command)
    $securePassword = ConvertTo-SecureString $password -AsPlainText -Force
}

# Create credential exactly like manual command
$cred = New-Object System.Management.Automation.PSCredential($Username, $securePassword)

# Execute exactly like your manual command - no extra parameters or formatting
Write-Host "Executing: Invoke-WebRequest -Uri '$TargetUrl' -Proxy '$ProxyUrl' -ProxyCredential `$cred"

try {
    $response = Invoke-WebRequest -Uri $TargetUrl -Proxy $ProxyUrl -ProxyCredential $cred
    Write-Host "SUCCESS: Status $($response.StatusCode)"
    Write-Host $response.Content
} catch {
    Write-Host "ERROR: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode)"
    }
}
