@echo off
setlocal enabledelayedexpansion

echo ==============================================================================
echo [1/3] Checking environment file...
echo ==============================================================================
cd /d "%~dp0\.."
if not exist .env (
    echo [.env] not found. Copying [.env.example] to [.env]...
    copy .env.example .env
)

echo ==============================================================================
echo [2/3] Starting Docker Infrastructure (MySQL:3309, Redis:6380, Kafka:9094)...
echo ==============================================================================
docker compose up -d ecom-mysql ecom-redis ecom-kafka
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to start Docker Compose infrastructure.
    pause
    exit /b %ERRORLEVEL%
)

echo Waiting for database and services to reach healthy state...
timeout /t 10 /nobreak > nul

docker compose ps

echo ==============================================================================
echo [3/3] Infrastructure is ready!
echo ------------------------------------------------------------------------------
echo - MySQL Host Port: 3309 (Databases: ecommerce_auth, ecommerce_product, etc.)
echo - Redis Host Port: 6380
echo - Kafka Host Port: 9094
echo ==============================================================================
echo To start microservices locally, run:
echo   cd product-service ^&^& mvn spring-boot:run
echo   cd frontend ^&^& npm run dev
echo ==============================================================================
