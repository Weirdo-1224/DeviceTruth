# 05-03 部署/备份/恢复/迁移失败演练（幂等、自包含：只用 compose 自己的 PG 与临时卷，结束即清理）
# 用法： powershell -ExecutionPolicy Bypass -File scripts/verify_deployment.ps1
# 前置： mvn -q package；cd frontend; npm ci; npm run build；docker 可用（本机已有 eclipse-temurin/nginx/pgvector 镜像）。
$ErrorActionPreference = 'Continue'   # docker 把进度写 stderr，避免被当成终止错误；关键命令显式检查退出码
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$out = Join-Path $root "outputs/validation/deployment-$stamp"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$log = Join-Path $out 'drill.log'
function Step($message) { $line = "[$(Get-Date -Format 'HH:mm:ss')] $message"; $line | Tee-Object -FilePath $log -Append }
function Fail($message) { Step "FAIL: $message"; throw $message }
function Invoke-JsonPost($uri, $object, $headers) {
    # PowerShell 5.1 直接用 -Body 字符串发中文会按本地代码页转码（服务端收到 ????）；
    # 统一写入 UTF-8 临时文件后 -InFile 上传，保证 JSON 正文与中文原样到达服务端。
    $file = Join-Path $out ('body-' + [guid]::NewGuid().ToString() + '.json')
    $json = $object | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($file, $json, (New-Object System.Text.UTF8Encoding($false)))
    $parameters = @{ Method = 'Post'; Uri = $uri; ContentType = 'application/json; charset=utf-8'; InFile = $file }
    if ($headers) { $parameters.Headers = $headers }
    Invoke-RestMethod @parameters
}
function Wait-Health($url, $seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing $url -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return $true }
        } catch { Start-Sleep -Seconds 3 }
    }
    return $false
}
function Compose([string[]] $arguments) {
    & docker compose --profile product @arguments 2>&1 | Out-Null
    return $LASTEXITCODE
}

Step 'check build artifacts'
if (-not (Test-Path 'target/devicetruth-core-0.1.0.jar')) { Fail 'missing fat JAR: run mvn -q package' }
if (-not (Test-Path 'frontend/dist/index.html')) { Fail 'missing frontend/dist: run npm run build' }

$env:DEVICETRUTH_DB_PASSWORD = 'drill-local-password'
$env:DEVICETRUTH_AGENT_REASONER = 'scripted'
$env:DEVICETRUTH_EMBEDDING_PROVIDER = 'deterministic'
$env:DEVICETRUTH_OWNER_ID = 'drill-owner'
$env:DEVICETRUTH_PROJECT_ID = 'drill-project'
Remove-Item Env:DEVICETRUTH_SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
$base = 'http://127.0.0.1:8080'

try {
    Step 'build images'
    if ((Compose @('build')) -ne 0) { Fail 'compose build failed' }

    Step 'compose up (fresh PG + file volume)'
    if ((Compose @('up', '-d')) -ne 0) { Fail 'compose up failed' }
    if (-not (Wait-Health "$base/api/health" 240)) {
        & docker compose --profile product logs backend | Out-File (Join-Path $out 'backend.log')
        Fail 'backend health timeout'
    }
    Step 'backend healthy'
    if (-not (Wait-Health 'http://127.0.0.1:8081/api/health' 60)) {
        & docker compose --profile product logs frontend | Out-File (Join-Path $out 'frontend.log')
        Fail 'frontend -> /api proxy not healthy'
    }
    Step 'frontend + /api proxy healthy (nginx -> backend)'

    # 导入演示手册（scripted 流程要求有可检索语料才会先追问补证）
    # 显式按 UTF-8 读取（PowerShell 5.1 的 Get-Content 默认按本地代码页，中文会变成乱码）
    $manual = [System.IO.File]::ReadAllText((Join-Path $root 'datasets/knowledge/charging-system-manual.md'), (New-Object System.Text.UTF8Encoding($false)))
    $document = @{ title = '充电系统检修手册（演示资料）'; domain = 'automotive-demo'; version = '2026.1'
        filename = 'manual.md'; mimeType = 'text/markdown'; content = $manual; dataNature = 'SIMULATED' } | ConvertTo-Json
    # PowerShell 5.1 直接发字符串会把中文按本地代码页转码导致 400；写 UTF-8 文件后 -InFile 上传
    $documentFile = Join-Path $out 'manual-import.json'
    [System.IO.File]::WriteAllText($documentFile, $document, (New-Object System.Text.UTF8Encoding($false)))
    try {
        Invoke-RestMethod -Method Post -Uri "$base/api/knowledge/documents" -ContentType 'application/json; charset=utf-8' -InFile $documentFile | Out-Null
    } catch {
        $stream = $_.Exception.Response.GetResponseStream()
        $bodyText = (New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)).ReadToEnd()
        Step ("manual import failed: HTTP " + [int]$_.Exception.Response.StatusCode + " body=" + $bodyText)
        throw
    }
    Step 'demo manual imported'

    # 产品链路：会话 → 消息（scripted 追问）→ 补答 → 完成 → 引用
    $conv = Invoke-JsonPost "$base/api/conversations" @{}
    $accepted = Invoke-JsonPost "$base/api/conversations/$($conv.conversationId)/messages" `
        @{ schemaVersion = 1; userMessage = '新能源汽车充电中断，没有故障码，先查什么资料？' } `
        @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
    $runId = $accepted.runId
    $deadline = (Get-Date).AddSeconds(120); $run = $null
    while ((Get-Date) -lt $deadline) {
        $run = Invoke-RestMethod -Uri "$base/api/runs/$runId"
        if ($run.status -in @('WAITING_FOR_USER', 'COMPLETED', 'FAILED')) { break }
        Start-Sleep -Milliseconds 500
    }
    if ($run.status -ne 'WAITING_FOR_USER') { Fail "unexpected first status $($run.status)" }
    $reply = @{ schemaVersion = 1; userMessage = '直流快充，SOC 约 40%'; replyTo = @{
        runId = $runId; pendingQuestionId = $run.pendingQuestion.pendingQuestionId
        questionVersion = $run.pendingQuestion.questionVersion } }
    Invoke-JsonPost "$base/api/conversations/$($conv.conversationId)/messages" $reply `
        @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() } | Out-Null
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        $run = Invoke-RestMethod -Uri "$base/api/runs/$runId"
        if ($run.status -in @('COMPLETED', 'FAILED')) { break }
        Start-Sleep -Milliseconds 500
    }
    if ($run.status -ne 'COMPLETED') { Fail "run did not complete: $($run.status)" }
    $refs = Invoke-RestMethod -Uri "$base/api/runs/$runId/references"
    Step "product run completed with $($refs.Count) references"

    # 重启持久性
    Step 'restart backend + postgres, verify persistence'
    if ((Compose @('restart', 'backend', 'postgres')) -ne 0) { Fail 'compose restart failed' }
    if (-not (Wait-Health "$base/api/health" 240)) { Fail 'backend did not recover after restart' }
    $detail = Invoke-RestMethod -Uri "$base/api/conversations/$($conv.conversationId)"
    if ($detail.runs.Count -lt 1 -or $detail.runs[-1].runId -ne $runId) { Fail 'conversation not restored after restart' }
    $refsAfter = Invoke-RestMethod -Uri "$base/api/runs/$runId/references"
    if ($refsAfter.Count -ne $refs.Count) { Fail 'references changed after restart' }
    Step 'restart persistence verified'

    # 备份 + 恢复到同实例的独立恢复库，行数对照
    Step 'pg_dump / pg_restore into devicetruth_restore'
    & docker compose --profile product exec -T postgres pg_dump -U devicetruth -d devicetruth -Fc -f /tmp/devicetruth.dump
    if ($LASTEXITCODE -ne 0) { Fail 'pg_dump failed' }
    & docker compose --profile product exec -T postgres createdb -U devicetruth devicetruth_restore
    if ($LASTEXITCODE -ne 0) { Fail 'createdb restore failed' }
    & docker compose --profile product exec -T postgres pg_restore -U devicetruth -d devicetruth_restore --no-owner /tmp/devicetruth.dump
    if ($LASTEXITCODE -ne 0) { Fail 'pg_restore failed' }
    $tables = @('dt_agent_run', 'dt_run_event', 'dt_message', 'dt_evidence_reference', 'dt_uploaded_file', 'dt_knowledge_document')
    foreach ($t in $tables) {
        $src = (& docker compose --profile product exec -T postgres psql -U devicetruth -d devicetruth -tAc "SELECT count(*) FROM $t").Trim()
        $dst = (& docker compose --profile product exec -T postgres psql -U devicetruth -d devicetruth_restore -tAc "SELECT count(*) FROM $t").Trim()
        if ($src -ne $dst) { Fail "row count mismatch on $t ($src vs $dst)" }
        Step "restore verified $t = $src"
    }
    Step 'backup/restore row counts verified'

    # 迁移失败：篡改 flyway 校验和，重启必须失败且不提供服务
    Step 'migration failure drill (checksum tamper)'
    & docker compose --profile product exec -T postgres psql -U devicetruth -d devicetruth -c "UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE success AND version IS NOT NULL" | Out-Null
    if ((Compose @('stop', 'backend')) -ne 0) { Fail 'stop backend failed' }
    & docker compose --profile product up -d backend 2>&1 | Out-Null
    $migrationFailed = -not (Wait-Health "$base/api/health" 40)
    & docker compose --profile product logs backend --tail 60 | Out-File (Join-Path $out 'migration-failure.log')
    if (-not $migrationFailed) { Fail 'tampered migration history did not block startup' }
    Step 'migration failure correctly blocks startup (no degraded service)'

    # 正向修复：后端切到恢复库（等价恢复备份），旧会话与引用仍可读
    Step 'forward-fix: point backend at restored database'
    if ((Compose @('stop', 'backend')) -ne 0) { Fail 'stop backend failed' }
    $env:DEVICETRUTH_SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres:5432/devicetruth_restore'
    & docker compose --profile product up -d --force-recreate backend 2>&1 | Out-Null
    if (-not (Wait-Health "$base/api/health" 240)) { Fail 'restored database backend failed to start' }
    $detail = Invoke-RestMethod -Uri "$base/api/conversations/$($conv.conversationId)"
    if ($detail.runs.Count -lt 1) { Fail 'restored conversation not readable' }
    $refsRestored = Invoke-RestMethod -Uri "$base/api/runs/$runId/references"
    if ($refsRestored.Count -ne $refs.Count) { Fail 'restored references mismatch' }
    Step 'forward-fix verified: conversation and references readable'

    $summary = @{ status = 'PASS'; runId = $runId; references = $refs.Count
                  restoreDatabase = 'devicetruth_restore'; evidence = $out }
    $summary | ConvertTo-Json | Tee-Object -FilePath (Join-Path $out 'result.json')
}
catch {
    Step "drill aborted: $($_.Exception.Message)"
    & docker compose --profile product logs backend --tail 80 | Out-File (Join-Path $out 'backend-crash.log')
    exit 1
}
finally {
    Step 'cleanup (compose down -v: only this drill volumes)'
    & docker compose --profile product down -v 2>&1 | Out-Null
}
