@echo off
title SentinelGrid - Healthcare Operations Platform Launcher
color 0A

echo =========================================================================
echo               SentinelGrid Emergency Medicine Platform                  
echo =========================================================================
echo.
echo Starting SentinelGrid Backend (Spring Boot on http://localhost:8080)...
start "SentinelGrid Backend (Spring Boot)" cmd /k "color 0B && title SentinelGrid Backend && mvn spring-boot:run"

echo.
echo Waiting 5 seconds for backend initialization...
timeout /t 5 /nobreak > nul

echo.
echo Starting SentinelGrid Frontend (React + Vite on http://localhost:3000)...
start "SentinelGrid Frontend (React + Vite)" cmd /k "color 0D && title SentinelGrid Frontend && cd frontend && npm run dev"

echo.
echo =========================================================================
echo  SentinelGrid servers are launching in separate windows!
echo  
echo  - Backend API:   http://localhost:8080
echo  - Frontend App:  http://localhost:3000
echo  - Swagger UI:    http://localhost:8080/swagger-ui.html
echo  - H2 Console:    http://localhost:8080/h2-console
echo.
echo  Demo Logins:
echo  - Patient:    patient1 / password123
echo  - Pharmacist: pharmacist1 / password123
echo  - Admin:      admin1 / password123
echo =========================================================================
echo.

echo Opening SentinelGrid Frontend in your default browser...
timeout /t 3 /nobreak > nul
start http://localhost:3000

pause
