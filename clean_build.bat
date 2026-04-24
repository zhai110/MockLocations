@echo off
chcp 65001 >nul
echo ========================================
echo 清理构建缓存
echo ========================================
echo.

echo [1/3] 停止 Gradle 守护进程...
call gradlew --stop 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Gradle 未运行或无需停止
)
echo.

echo [2/3] 删除构建缓存...
if exist "app\build" (
    rmdir /s /q "app\build"
    echo ✓ app\build 已删除
) else (
    echo - app\build 不存在
)

if exist ".gradle" (
    echo - .gradle 目录保留（包含依赖缓存）
)
echo.

echo [3/3] 清理完成！
echo.
echo ========================================
echo 现在可以在 Android Studio 中重新构建：
echo ========================================
echo.
echo 1. File → Invalidate Caches / Restart
echo 2. Build → Clean Project
echo 3. Build → Rebuild Project
echo 4. Build → Generate Signed Bundle / APK
echo.
echo ========================================
echo.

pause
