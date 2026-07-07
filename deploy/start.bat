@echo off
setlocal enabledelayedexpansion

set MODE=%1
if "%MODE%"=="" set MODE=local

cd /d "%~dp0"

set PROFILES=
if "%MODE%"=="local" (
  set ENV_TEMPLATE=.env.local.example
  set PROFILES=--profile localdb
) else if "%MODE%"=="production" (
  set ENV_TEMPLATE=.env.production.example
  set PROFILES=--profile localdb
) else if "%MODE%"=="external" (
  set ENV_TEMPLATE=.env.external.example
  set PROFILES=
) else if "%MODE%"=="tools" (
  set ENV_TEMPLATE=.env.local.example
  set PROFILES=--profile localdb --profile tools
) else (
  echo Usage: start.bat [local^|production^|external^|tools]
  exit /b 1
)

where docker >nul 2>&1
if errorlevel 1 (
  echo Docker is not installed or not in PATH.
  exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
  echo Docker Compose v2 is not available.
  exit /b 1
)

if not exist ".env" (
  copy "%ENV_TEMPLATE%" ".env" >nul
  echo Created deploy\.env from %ENV_TEMPLATE%. Review it before production use.
)

echo Starting AgentX in %MODE% mode...
docker compose %PROFILES% up -d --build
if errorlevel 1 exit /b 1

echo.
echo AgentX is starting.
echo Frontend: http://localhost:3000
echo Backend:  http://localhost:8088/api/health
echo Logs:     docker compose logs -f
