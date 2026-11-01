# ==============================================================================
# SentinelGrid — Local Backend Launcher
# Automatically loads variables from .env and starts Spring Boot
# ==============================================================================

$envFile = Join-Path $PSScriptRoot ".env"

if (!(Test-Path $envFile)) {
    Write-Host "[WARN] .env file not found at $envFile" -ForegroundColor Yellow
    Write-Host "[INFO] Creating template .env file..." -ForegroundColor Cyan
    @"
# SentinelGrid Local Environment Configuration
SPRING_PROFILES_ACTIVE=local
SPRING_DATASOURCE_PASSWORD=
GEMINI_API_KEY=
"@ | Set-Content -Path $envFile -Encoding UTF8
    Write-Host "[INFO] Created .env file. Please edit it to add your database password and GEMINI_API_KEY." -ForegroundColor Green
}

# Parse .env line by line
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()

    if ($line -and !$line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line -split "=", 2
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()

        # Strip surrounding double or single quotes if present
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

# Ensure baseline defaults for local developer convenience
if (![Environment]::GetEnvironmentVariable("SPRING_PROFILES_ACTIVE", "Process")) {
    [Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", "local", "Process")
}

if (![Environment]::GetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", "Process")) {
    Write-Host "[WARN] SPRING_DATASOURCE_PASSWORD is not set in .env. Database connection may require credentials." -ForegroundColor Yellow
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting SentinelGrid Backend with Profile: $env:SPRING_PROFILES_ACTIVE" -ForegroundColor Green
if ($env:GEMINI_API_KEY) {
    Write-Host " Gemini API Key: Configured ([REDACTED])" -ForegroundColor Green
} else {
    Write-Host " Gemini API Key: Not set (will use deterministic SQL fallback)" -ForegroundColor Yellow
}
Write-Host "==========================================================" -ForegroundColor Cyan

mvn spring-boot:run

