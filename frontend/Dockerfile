# 前端静态站点镜像（05-03）：构建产物由宿主 `cd frontend && npm run build` 生成，
# 镜像只做静态托管与 /api 反向代理，不包含 Node 依赖或任何密钥。
FROM nginx:1.27-alpine

COPY frontend/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8081
