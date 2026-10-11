@echo off
chcp 65001 >nul
title 智能记账 - 构建 APK
echo ========================================
echo   智能记账 - Android APK 构建
echo ========================================
echo.

set "JAVA_HOME=%~dp0..\jdk21\jdk-21.0.12.1+1"
set "GRADLE_USER_HOME=%~dp0.gradle-home"
set "ANDROID_HOME=%~dp0..\android-sdk"

rem 按当前所在位置自动生成 SDK 路径，项目移动后无需手动改配置
set "SDK_PATH=%~dp0..\android-sdk"
set "SDK_PATH=%SDK_PATH:\=/%"
> "%~dp0android\local.properties" echo sdk.dir=%SDK_PATH%

rem 同步最新网页代码到 Android 工程
echo 正在同步网页代码...
cd /d "%~dp0"
call npx cap sync android >nul 2>&1
if errorlevel 1 call "%LOCALAPPDATA%\nodejs\npx.cmd" cap sync android >nul 2>&1

echo 正在构建 Debug APK...
echo.

cd /d "%~dp0android"
call gradlew.bat assembleDebug --no-daemon

if %errorlevel%==0 (
    echo.
    echo ========================================
    echo   构建成功！
    echo ========================================
    echo.
    echo APK 位置: android\app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo 安装到手机:
    echo   1. 手机开启「开发者选项 - USB 调试」
    echo   2. USB 连接电脑，运行: adb install app-debug.apk
    echo   3. 或直接复制 APK 到手机点击安装
    echo.
) else (
    echo.
    echo [错误] 构建失败，请检查错误信息。
    echo.
)
pause
