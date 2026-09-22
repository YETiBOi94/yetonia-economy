@echo off
setlocal

:: Jump directly to the folder where this batch file lives
cd /d "%~dp0"

:: Run the gradle build wrapper
call gradlew.bat build --no-daemon

if errorlevel 1 (
    echo.
    echo Build failed!
) else (
    echo.
    echo Build complete! JARs are in build\libs\
)

pause
endlocal