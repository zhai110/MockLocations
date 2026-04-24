@echo off
chcp 65001 >nul
echo ========================================
echo 开始构建 Release APK
echo ========================================
echo.

REM 检查签名文件是否存在
if not exist "app\release.jks" (
    echo [错误] 签名文件不存在: app\release.jks
    echo.
    pause
    exit /b 1
)

echo [信息] 签名文件已找到
echo [信息] 正在清理之前的构建...
echo.

REM 清理之前的构建
if exist "app\build\outputs\apk\release" (
    rmdir /s /q "app\build\outputs\apk\release"
)

echo ========================================
echo 请在 Android Studio 中执行以下操作：
echo ========================================
echo.
echo 1. 点击菜单: Build → Generate Signed Bundle / APK
echo 2. 选择: APK
echo 3. 确认签名配置已自动填充
echo 4. 选择: release 构建类型
echo 5. 勾选: V1 和 V2 签名方案
echo 6. 点击: Finish
echo.
echo ========================================
echo 或者使用命令行（需要配置 Gradle）:
echo ========================================
echo.
echo gradlew assembleRelease
echo.
echo ========================================
echo.

pause
