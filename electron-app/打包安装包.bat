@echo off
chcp 65001 >nul
title 智能记账 - 打包安装包
echo ========================================
echo   智能记账桌面版 - 打包安装包
echo ========================================
echo.
echo 正在配置国内镜像源...
set ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
set ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/

echo.
echo 正在打包 Windows 安装包（首次打包需要下载依赖，请耐心等待）...
echo.
call npm run build:win

if %errorlevel%==0 (
    echo.
    echo ========================================
echo   打包成功！
echo ========================================
echo.
echo 安装包位于 dist 目录下：
echo   - 智能记账-1.0.0-x64.exe        （安装版，推荐）
echo   - 智能记账 1.0.0.exe           （免安装版，直接运行）
echo.
explorer dist
) else (
    echo.
    echo [错误] 打包失败，请检查错误信息。
    echo.
)
pause
