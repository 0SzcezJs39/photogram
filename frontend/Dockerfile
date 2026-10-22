FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
# npm ci (в отличие от npm install) требует и package.json, и package-lock.json
# npm install используется для разработки и обновления зависимостей, в то время как npm ci (Clean Install) предназначен для сборки проекта на серверах непрерывной интеграции (CI/CD) и продакшене.
RUN npm ci
COPY . .
# npm ci — чистая установка зависимостей строго по package-lock.json (точные версии, без обновления lock-файла); используется в CI/Docker.
# npm run build — запускает скрипт build из package.json, то есть vite build: собирает оптимизированные статические файлы в dist/.
RUN npm run build

FROM nginx:alpine AS app
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
# команда запуска по умолчанию. nginx стартует, -g "daemon off;" отключает демонизацию, чтобы мастер-процесс nginx оставался в foreground (PID 1). Это обязательное условие для Docker: если процесс завершится/форкнется в фон, контейнер посчитается остановленным.
CMD ["nginx", "-g", "daemon off;"]
