@echo off
setlocal

rem Check if mvn is available in PATH
where mvn >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "MVN_CMD=mvn"
    goto run
)

rem Check user .m2 wrapper directory for maven executable
for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.m2\*mvn.cmd" 2^>nul') do (
    set "MVN_CMD=%%i"
    goto run
)

echo [ERROR] Maven (mvn) was not found in PATH or in %USERPROFILE%\.m2.
echo Please ensure Apache Maven is installed or added to your system PATH.
exit /b 1

:run
echo [Priora] Starting Spring Boot Backend (Profile: dev, Port: 8081)...
"%MVN_CMD%" spring-boot:run -Dspring-boot.run.profiles=dev
