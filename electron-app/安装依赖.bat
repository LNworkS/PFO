@echo off
chcp 65001 >nul
title 智能记账 - 安装依赖
echo ========================================
echo   智能记账桌面版 - 安装依赖
echo ========================================
echo.
echo 正在配置国内镜像源，加速下载...
set ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
set ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/

echo.
echo 正在安装依赖（首次安装需要几分钟，请耐心等待）...
echo.
call npm install --registry=https://registry.npmmirror.com

if %errorlevel%==0 (
    echo.
    echo ========================================
echo   依赖安装成功！
echo ========================================
echo.
echo 接下来可以运行：
echo   启动开发版：双击 "运行开发版.bat"
echo   打包安装包：双击 "打包安装包.bat"
echo.
) else (
    echo.
    echo [错误] 依赖安装失败，请检查网络连接后重试。
    echo.
)
pause
