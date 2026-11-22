# 一键从当前工作区代码构建并启动产品全套服务（PowerShell）。
# 用法：./scripts/dev-up.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

# JDK 21（优先用仓库自带 .tools/jdk-21*，找不到则沿用当前 JAVA_HOME）
$jdk = Get-ChildItem -Directory -Path '.tools' -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) {
    $env:JAVA_HOME = $jdk.FullName
    $env:PATH = "$($jdk.FullName)\bin;$env:PATH"
}
Write-Host "==> Java: $((java -version 2>&1 | Select-Object -First 1))"

Write-Host '==> [1/4] 构建后端 JAR'
mvn -q package -DskipTests

Write-Host '==> [2/4] 构建前端 dist（含类型检查）'
Push-Location frontend
try { npm run build } finally { Pop-Location }

Write-Host '==> [3/4] 构建镜像（backend + frontend）'
docker compose --profile product build

Write-Host '==> [4/4] 启动服务'
docker compose --profile product up -d

Write-Host '==> 等待后端健康检查'
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $health = Invoke-RestMethod -Uri 'http://localhost:8080/api/health' -TimeoutSec 3
        $healthy = $true
        break
    } catch { Start-Sleep -Seconds 2 }
}
if ($healthy) { Write-Host "    后端: $($health | ConvertTo-Json -Compress)" }
else { Write-Host '    后端健康检查超时，请查看 docker logs devicetruth-backend-1' }
Write-Host '    前端: http://localhost:8081  （开发热更新入口 http://localhost:5173 需另起 npm run dev）'
