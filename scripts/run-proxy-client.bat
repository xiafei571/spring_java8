@echo off
REM Enterprise Proxy Client - Windows Batch Launcher
REM This batch file runs the PowerShell proxy client script

echo Starting Enterprise Proxy Client (PowerShell)...
echo ===============================================

REM Change to the script directory
cd /d "%~dp0"

REM Check if PowerShell is available
powershell -Command "Write-Host 'PowerShell is available'" >nul 2>&1
if errorlevel 1 (
    echo ERROR: PowerShell is not available or not in PATH
    echo Please ensure PowerShell is installed and accessible
    pause
    exit /b 1
)

REM Check if the PowerShell script exists
if not exist "proxy-client.ps1" (
    echo ERROR: proxy-client.ps1 not found in current directory
    echo Please ensure the script file exists
    pause
    exit /b 1
)

REM Run the PowerShell script with bypass execution policy
echo Running PowerShell proxy client...
powershell -ExecutionPolicy Bypass -File "proxy-client.ps1"

REM Check the exit code
if errorlevel 1 (
    echo.
    echo ERROR: Proxy client failed with error code %errorlevel%
) else (
    echo.
    echo SUCCESS: Proxy client completed successfully
)

echo.
echo Press any key to exit...
pause >nul
