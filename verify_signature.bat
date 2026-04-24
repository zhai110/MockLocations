@echo off
echo ========================================
echo Verifying APK Signature
echo ========================================
echo.

set APK_PATH=app\build\outputs\apk\release\app-release.apk

if not exist "%APK_PATH%" (
    echo ERROR: APK not found! Please build first.
    pause
    exit /b 1
)

echo APK Path: %APK_PATH%
echo.
echo Checking signature...
echo.

REM 使用 apksigner 验证签名（需要 Android SDK）
where apksigner >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    apksigner verify --verbose "%APK_PATH%"
) else (
    echo apksigner not found in PATH.
    echo Trying to find it in Android SDK...
    echo.
    
    REM 尝试从 local.properties 获取 SDK 路径
    for /f "tokens=2 delims==" %%a in ('findstr "sdk.dir" local.properties') do set SDK_DIR=%%a
    
    if defined SDK_DIR (
        set APKSIGNER=%SDK_DIR%\build-tools\*\apksigner.bat
        for %%i in (%APKSIGNER%) do (
            if exist "%%i" (
                echo Using: %%i
                call "%%i" verify --verbose "%APK_PATH%"
                goto :end
            )
        )
    )
    
    echo Could not find apksigner. Please check manually.
)

:end
echo.
pause
