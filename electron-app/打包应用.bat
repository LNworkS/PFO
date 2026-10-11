@echo off
chcp 65001 >nul
title 智能记账 - 打包应用
echo ========================================
echo   智能记账桌面版 - 打包
echo ========================================
echo.
echo 正在配置国内镜像源...
set ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
set ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/

echo.
echo 正在打包 Windows 应用...
echo.
call npx electron-packager . "智能记账" --platform=win32 --arch=x64 --out=dist --overwrite

if %errorlevel%==0 (
    echo.
    echo ========================================
echo   打包成功！
echo ========================================
echo.
echo 应用位于 dist\智能记账-win32-x64\智能记账.exe
echo.
explorer dist
) else (
    echo.
    echo [错误] 打包失败，请检查错误信息。
    echo.
)
pause
