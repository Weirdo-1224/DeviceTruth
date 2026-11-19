# 产品服务镜像（05-03）：只打包 fat JAR 与 product profile 启动入口，不含密钥、数据或前端源码。
# JAR 由宿主 `mvn -q package` 产出后复制进来；知识原件以独立数据包用于导入，不进服务镜像。
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/devicetruth-core-0.1.0.jar /app/devicetruth.jar
# 标准 Skill 与资产夹具按相对路径从工作目录读取（devicetruth.skills.root=skills、datasets/v1），
# 必须随镜像交付，否则 SkillRuntime 启动失败。
COPY skills /app/skills
COPY datasets /app/datasets

# 产品 HTTP 入口（product profile 才装配 PostgreSQL/知识/Agent；CLI 直接读同一 fat JAR）
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD ["sh", "-c", "curl -fsS http://127.0.0.1:8080/api/health >/dev/null 2>&1 || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dloader.main=io.devicetruth.server.DeviceTruthServerApplication -cp /app/devicetruth.jar org.springframework.boot.loader.launch.PropertiesLauncher --spring.profiles.active=product --server.port=8080 --debug=false"]
