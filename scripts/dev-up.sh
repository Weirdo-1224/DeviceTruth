#!/usr/bin/env bash
# 一键从当前工作区代码构建并启动产品全套服务（Git Bash）。
# 用法：bash scripts/dev-up.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# JDK 21（优先用仓库自带 .tools/jdk-21*，找不到则沿用当前 JAVA_HOME）
JDK_DIR="$(ls -d .tools/jdk-21* 2>/dev/null | head -1 || true)"
if [ -n "$JDK_DIR" ]; then
  export JAVA_HOME="$PWD/$JDK_DIR"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
echo "==> Java: $(java -version 2>&1 | head -1)"

echo "==> [1/4] 构建后端 JAR"
mvn -q package -DskipTests

echo "==> [2/4] 构建前端 dist（含类型检查）"
(cd frontend && npm run build)

echo "==> [3/4] 构建镜像（backend + frontend）"
docker compose --profile product build

echo "==> [4/4] 启动服务"
docker compose --profile product up -d

echo "==> 等待后端健康检查"
for _ in $(seq 1 30); do
  if curl -sf http://localhost:8080/api/health >/dev/null 2>&1; then break; fi
  sleep 2
done
echo "    后端: $(curl -s http://localhost:8080/api/health)"
echo "    前端: http://localhost:8081  （开发热更新入口 http://localhost:5173 需另起 npm run dev）"
