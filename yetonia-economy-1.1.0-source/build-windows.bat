@echo off
setlocal
where java >nul 2>nul || (echo Java not found. Install Java 25 and try again.& exit /b 1)
java -version
where gradle >nul 2>nul || (echo Gradle not found. Install Gradle 9.5.1 and try again.& exit /b 1)
gradle build --no-daemon
if errorlevel 1 exit /b %errorlevel%
echo.
echo Build complete. JARs are in build\libs\
endlocal
