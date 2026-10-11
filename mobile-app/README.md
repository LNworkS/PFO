# 智能记账 - Android 移动端 App

基于 Capacitor 6 打包的个人财务管理 Android 应用。

## 已构建产物

**`智能记账.apk`**（工作区根目录，3.6 MB）— Debug 版，可直接安装测试

## 安装到手机

1. **USB 方式**：手机开启「开发者选项 → USB 调试」，连接电脑后执行：
   ```
   adb install 智能记账.apk
   ```
2. **直接方式**：把 APK 复制到手机（微信/QQ 发送、网盘、数据线拷贝均可），点击安装
   > Android 需允许「安装未知来源应用」

## 重新构建 APK

双击运行 **`构建APK.bat`**（已配置好 JDK 和 Gradle 环境）

产物位置：`android\app\build\outputs\apk\debug\app-debug.apk`

## 项目结构

```
mobile-app/
├── www/                  # Web 应用源码（从 index.html 复制）
├── android/              # Android 原生工程（Capacitor 生成）
├── capacitor.config.json # Capacitor 配置
├── 构建APK.bat           # 一键构建脚本
└── package.json
```

## 数据存储

- 数据保存在 App 内部存储（`/data/data/com.smartaccounting.mobile/`）
- 全程 AES-256-GCM 加密，密码保护
- 可通过「设置 → 导出数据」备份

## 常用命令

```bash
# 同步 Web 代码到 Android 工程
npx cap sync android

# 构建 Debug APK
cd android && gradlew.bat assembleDebug

# 构建 Release APK（需配置签名）
cd android && gradlew.bat assembleRelease
```
