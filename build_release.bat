@echo off
echo ========================================
echo Building Release APK with Release Signing
echo ========================================
echo.

REM 检查签名文件是否存在
if not exist "app\release.jks" (
    echo ERROR: release.jks not found!
    pause
    exit /b 1
)

echo Starting Gradle build...
call gradlew.bat assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build SUCCESS!
    echo ========================================
    echo.
    echo APK location: app\build\outputs\apk\release\app-release.apk
    echo.
    
    REM 显示 APK 信息
    if exist "app\build\outputs\apk\release\app-release.apk" (
        echo File size:
        for %%A in ("app\build\outputs\apk\release\app-release.apk") do echo   %%~zA bytes
        echo.
        echo Modified: %%~tA
    )
) else (
    echo.
    echo ========================================
    echo Build FAILED!
    echo ========================================
)

pause
