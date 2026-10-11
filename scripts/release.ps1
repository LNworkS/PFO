<#
  智能记账 - 一键发版脚本（Android）

  用法：
    .\scripts\release.ps1                        自动递增补丁版本号并发布
    .\scripts\release.ps1 -Version 1.1.0         指定版本号
    .\scripts\release.ps1 -Notes "修复若干问题"   指定更新说明
    .\scripts\release.ps1 -SkipPush              只构建，不提交推送

  流程：递增版本号 -> 同步网页代码 -> 构建签名 APK -> 归档安装包
        -> 生成 version.json -> 刷新 CDN 缓存 -> 提交并推送
#>
param(
    [string]$Version = '',
    [string]$Notes = '',
    [switch]$SkipPush
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$buildGradle = Join-Path $root 'mobile-app\android\app\build.gradle'
$rootHtml = Join-Path $root 'index.html'
$wwwHtml = Join-Path $root 'mobile-app\www\index.html'
$versionJson = Join-Path $root 'update\version.json'
$releasesDir = Join-Path $root 'releases'
$apkBuilt = Join-Path $root 'mobile-app\android\app\build\outputs\apk\release\app-release.apk'

$repo = 'LNworkS/PFO'

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    # PowerShell 的 Set-Content -Encoding UTF8 会写入 BOM，会让 Gradle / JSON 解析出错
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function Step([string]$msg) {
    Write-Host ''
    Write-Host "==> $msg" -ForegroundColor Cyan
}

# ---------- 1. 计算新版本号 ----------
Step '读取当前版本号'
$gradleText = [System.IO.File]::ReadAllText($buildGradle)
if ($gradleText -notmatch 'versionCode\s+(\d+)') { throw '未能从 build.gradle 解析出 versionCode' }
$curCode = [int]$Matches[1]
if ($gradleText -notmatch 'versionName\s+"([^"]+)"') { throw '未能从 build.gradle 解析出 versionName' }
$curName = $Matches[1]

if (-not $Version) {
    $parts = @($curName.Split('.'))
    while ($parts.Count -lt 3) { $parts += '0' }
    $parts[$parts.Count - 1] = ([int]$parts[$parts.Count - 1] + 1).ToString()
    $Version = $parts -join '.'
}
$newCode = $curCode + 1
Write-Host "当前 v$curName ($curCode)  ->  新版本 v$Version ($newCode)"

# ---------- 2. 写入新版本号 ----------
Step '更新版本号'
$gradleText = $gradleText -replace 'versionCode\s+\d+', "versionCode $newCode"
$gradleText = $gradleText -replace 'versionName\s+"[^"]+"', "versionName `"$Version`""
Write-Utf8NoBom $buildGradle $gradleText

$html = [System.IO.File]::ReadAllText($rootHtml)
if ($html -notmatch "VERSION_CODE:\s*\d+") { throw 'index.html 中未找到 VERSION_CODE' }
$html = $html -replace "(VERSION_CODE:\s*)\d+", "`${1}$newCode"
$html = $html -replace "(VERSION_NAME:\s*')[^']*'", "`${1}$Version'"
Write-Utf8NoBom $rootHtml $html
Copy-Item $rootHtml $wwwHtml -Force
Write-Host "已同步 index.html 与 mobile-app/www/index.html"

# ---------- 3. 构建签名 APK ----------
Step '同步网页代码并构建签名 APK'
$env:JAVA_HOME = Join-Path $root 'jdk21\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME = Join-Path $root 'mobile-app\.gradle-home'
$env:ANDROID_HOME = Join-Path $root 'android-sdk'
Write-Utf8NoBom (Join-Path $root 'mobile-app\android\local.properties') ("sdk.dir=" + ((Join-Path $root 'android-sdk') -replace '\\', '/') + "`n")

# 必须走 npx.cmd：直接写 npx 会被解析到 npx.ps1，而本机禁用了脚本执行
$npx = Join-Path $env:LOCALAPPDATA 'nodejs\npx.cmd'
if (-not (Test-Path $npx)) { $npx = 'npx.cmd' }
Push-Location (Join-Path $root 'mobile-app')
& $npx cap sync android | Out-Null
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'npx cap sync 执行失败' }
Pop-Location

Push-Location (Join-Path $root 'mobile-app\android')
& .\gradlew.bat assembleRelease --no-daemon
$buildOk = ($LASTEXITCODE -eq 0)
Pop-Location
if (-not $buildOk) { throw 'APK 构建失败' }
if (-not (Test-Path $apkBuilt)) { throw "未找到构建产物：$apkBuilt" }

# ---------- 4. 归档安装包 ----------
Step '归档安装包'
New-Item -ItemType Directory -Path $releasesDir -Force | Out-Null
$apkName = "PFO-$Version.apk"
Copy-Item $apkBuilt (Join-Path $releasesDir $apkName) -Force
$sizeMb = [math]::Round((Get-Item (Join-Path $releasesDir $apkName)).Length / 1MB, 2)
Write-Host "已输出 releases\$apkName ($sizeMb MB)"

# ---------- 5. 生成更新清单 ----------
Step '生成 update/version.json'
if (-not $Notes) { $Notes = '本次更新包含问题修复与优化' }
$manifest = [ordered]@{
    versionCode     = $newCode
    versionName     = $Version
    apkUrl          = "https://cdn.jsdelivr.net/gh/$repo@main/releases/$apkName"
    apkUrlFallback  = "https://raw.githubusercontent.com/$repo/main/releases/$apkName"
    changelog       = $Notes
    publishedAt     = (Get-Date -Format 'yyyy-MM-dd')
}
Write-Utf8NoBom $versionJson ($manifest | ConvertTo-Json -Depth 3)
Write-Host "更新说明：$Notes"

# ---------- 6. 提交并推送 ----------
if ($SkipPush) {
    Step '已跳过推送（-SkipPush）'
    Write-Host '本地改动已就绪，确认无误后可手动 git push。'
    exit 0
}

Step '提交并推送'
Push-Location $root
git add -A
git commit -m "release: v$Version"
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'git commit 失败' }
git tag "v$Version"
git push origin main
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'git push origin main 失败（请确认 SSH 密钥已配置）' }
git push origin "v$Version"
Pop-Location

# ---------- 7. 刷新 CDN 缓存 ----------
Step '刷新 jsDelivr 缓存'
foreach ($p in @("update/version.json", "releases/$apkName")) {
    try {
        Invoke-RestMethod -Uri "https://purge.jsdelivr.net/gh/$repo@main/$p" -TimeoutSec 30 | Out-Null
        Write-Host "已刷新 $p"
    } catch {
        Write-Host "刷新 $p 失败（不影响发布，CDN 最长 12 小时后自然生效）"
    }
}

Write-Host ''
Write-Host "发布完成：v$Version" -ForegroundColor Green
Write-Host "用户下次打开应用即可收到更新提示。"