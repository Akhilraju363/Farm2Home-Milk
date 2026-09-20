@echo off
REM Thin wrapper only - all startup logic lives in start-all-services.ps1.
REM %~dp0 resolves to this .bat's own folder regardless of the current
REM directory it's launched from, so there is nothing to configure.

setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all-services.ps1" %*
set EXIT_CODE=%ERRORLEVEL%

endlocal & exit /b %EXIT_CODE%
