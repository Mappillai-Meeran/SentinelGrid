@echo off
title SentinelGrid - Healthcare Operations Platform Launcher
color 0A

echo =========================================================================
echo               SentinelGrid Emergency Medicine Platform                  
echo =========================================================================
echo.

:: Load environment variables from .env if present
if exist "%~dp0.env" (
    echo Loading configuration from .env file...
    for /f "usebackq tokens=1* delims==" %%A in ("%~dp0.env") do (
        if not "%%A"=="" (
            echo %%A | findstr /r "^#" >nul || set "%%A=%%B"
        )
    )
)

:: Set default profile for local development
if "%SPRING_PROFILES_ACTIVE%"=="" set "SPRING_PROFILES_ACTIVE=local"

echo Active Spring Profile: %SPRING_PROFILES_ACTIVE%
if not "%GEMINI_API_KEY%"=="" (
    echo Gemini AI Search:     Configured
) else (
    echo Gemini AI Search:     Fallback mode (deterministic SQL)
)
echo.

echo Starting SentinelGrid Backend (Spring Boot on http://localhost:8080)...
start "SentinelGrid Backend (Spring Boot)" cmd /k "color 0B && title SentinelGrid Backend && set SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%&& set SPRING_DATASOURCE_PASSWORD=%SPRING_DATASOURCE_PASSWORD%&& set GEMINI_API_KEY=%GEMINI_API_KEY%&& mvn spring-boot:run"

echo.
echo Waiting 5 seconds for backend initialization...
timeout /t 5 /nobreak > nul

echo.
echo Starting SentinelGrid Frontend (React + Vite on http://localhost:3000)...
start "SentinelGrid Frontend (React + Vite)" cmd /k "color 0D && title SentinelGrid Frontend && cd /d "%~dp0frontend" && npm run dev"

echo.
echo =========================================================================
echo  SentinelGrid servers are launching in separate windows!
echo  
echo  - Backend API:   http://localhost:8080
echo  - Frontend App:  http://localhost:3000
echo  - Swagger UI:    http://localhost:8080/swagger-ui.html
echo.
echo  Demo Logins (Password: password123):
echo  - Patient:    patient1
echo  - Pharmacist: pharmacist1 (Puducherry) / pharmacist-chennai-anna (Chennai)
echo  - Manager:    manager1 (Puducherry) / manager-chennai-anna (Chennai)
echo  - Admin:      admin1 (All cities)
echo =========================================================================
echo.

echo Opening SentinelGrid Frontend in your default browser...
timeout /t 3 /nobreak > nul
start http://localhost:3000

pause
