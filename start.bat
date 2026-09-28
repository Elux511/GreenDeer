@echo off
rem ================================================================
rem  GreenDeer one-click launcher for Windows.
rem  Double-click this file.
rem
rem  DB credentials - pick one:
rem    1) copy local.env.example to local.env and fill DB_PASSWORD;
rem       the script loads it automatically. local.env is git-ignored.
rem    2) or run from a shell where DB_PASSWORD is already set.
rem
rem  First run compiles the sources offline automatically.
rem  Stop: Ctrl+C / close the window, or taskkill the java on 8080.
rem ================================================================
setlocal
cd /d "%~dp0"

rem ---- first run: create local.env via wizard ----
if not exist "local.env" (
  echo [first-run] local.env not found - asking for MySQL password ...
  powershell -NoProfile -ExecutionPolicy Bypass -File "tools\ensure-local-env.ps1"
  if errorlevel 1 (
    echo [error] setup cancelled. Rerun start.bat, or create local.env manually.
    pause
    exit /b 1
  )
)

rem ---- load local.env ----
for /f "usebackq tokens=1,* delims==" %%a in ("local.env") do (
  if /i "%%a"=="DB_PASSWORD" set "DB_PASSWORD=%%b"
  if /i "%%a"=="DB_USER"     set "DB_USER=%%b"
  if /i "%%a"=="DB_HOST"     set "DB_HOST=%%b"
  if /i "%%a"=="DB_PORT"     set "DB_PORT=%%b"
  if /i "%%a"=="DB_NAME"     set "DB_NAME=%%b"
)

if not defined DB_PASSWORD (
  echo [warn] DB_PASSWORD not set - trying empty password. See local.env.example.
)

rem ---- first run: offline compile ----
if not exist "target\classes\com\it\greendeer\GreenDeerApplication.class" (
  echo [boot] compiling sources offline. First run takes about one minute.
  powershell -NoProfile -ExecutionPolicy Bypass -File "tools\verify.ps1" -SkipRun
  if errorlevel 1 (
    echo.
    echo [error] compile failed. Check the output above.
    pause
    exit /b 1
  )
)

echo [boot] starting GreenDeer ...
echo        console : http://127.0.0.1:8080/console.html
echo        api     : http://127.0.0.1:8080/api/v1/sessions
echo        stop    : close this window / Ctrl+C
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "tools\run-offline.ps1"
echo.
echo [boot] stopped.
pause
