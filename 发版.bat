@echo off
chcp 65001 >nul
title 智能记账 - 一键发版
echo ========================================
echo   智能记账 - 一键发版
echo ========================================
echo.
echo 将自动完成：递增版本号 - 构建签名 APK - 生成更新清单 - 推送
echo.
set /p NOTES=请输入本次更新说明（可留空直接回车）: 
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\release.ps1" -Notes "%NOTES%"

echo.
pause