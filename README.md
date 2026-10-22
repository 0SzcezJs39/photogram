# Photogram
Фотогалерея с хэштегами и лайками: зарегистрированные пользователи загружают фотографии,
все видят общую ленту, зарегистрированные ставят лайки и видят, кто именно поставил лайк.
Авторизация stateless — JWT.
Спецификация: [`ts.txt`](ts.txt).
## Стек

| Слой | Технологии |
| --- | --- |
| Backend | Spring Boot 4.1.1, Java 21, Spring Security, Spring Data JPA, AWS SDK v2 (S3) |
| Frontend | Vue 3, vue-router, Vite 6 (обычный JS, без TypeScript и стора) |
| БД | MariaDB 13 (схема управляется Hibernate `ddl-auto=update`) |
| S3 | Garage 2.3 — S3-совместимое хранилище для байтов картинок |
| Авторизация | JWT HS256, stateless |
## Быстрый старт
```bash
cp .env.example .env
# заполнить .env; JWT_SECRET сгенерировать так:
openssl rand -base64 48
docker compose --profile init up -d   # первый запуск: инициализация Garage (layout, ключ, бакет)
docker compose up -d --build          # поднять весь стек
```
Сервисы:

| Сервис | Порт | Назначение |
| --- | --- | --- |
| `frontend` | 8080 | SPA, отдаётся nginx (в т.ч. SPA history fallback) |
| `backend` | 8082 | REST API под context-path `/api` |
| `mariadb` | 3306 | База данных |
| `garage` | 3900 | S3 API |
Профиль `init` нужен только для первого запуска: он создаёт layout, импортирует ключ
`app-key` и создаёт бакет. Операция идемпотентна, но повторять её на каждый запуск не нужно.
## Конфигурация
Все настройки приходят из переменных окружения — в репозитории нет ни одного секрета.
Полный список — в [`.env.example`](.env.example):

| Переменная | Назначение |
| --- | --- |
| `MYSQL_DATABASE` | Имя базы данных |
| `MYSQL_USER` / `MYSQL_PASSWORD` | Креденшелы приложения к БД |
| `MYSQL_ROOT_PASSWORD` | Пароль root в MariaDB |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | Ключи доступа к S3 (Garage) |
| `S3_BUCKET_NAME` | Имя бакета для картинок |
| `JWT_SECRET` | Секрет подписи JWT, **обязателен**, длина ≥ 32 байт |
`JWT_SECRET` не имеет значения по умолчанию: если переменная не задана, приложение
не стартует. Это сделано намеренно — чтобы дефолтный секрет нельзя было случайно
использовать в публичном окружении.
### Ротация секретов
- **JWT** — сгенерируйте новый (`openssl rand -base64 48`), положите в `.env` и
  перезапустите backend. Старые токены после этого перестают работать.
- **Пароли БД** — `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` меняются в самой MariaDB
  (`ALTER USER 'myapp_user'@'%' IDENTIFIED BY '...'`), а не только в `.env`: переменные
  в `.env` применяются к контейнеру только при первой инициализации базы.
- **Ключ S3** — воспользуйтесь скриптом `./scripts/rotate-s3-key.sh`: он выпускает
  случайный ключ, переносит права на бакет, переключает `.env`, перезапускает backend
  и только после успешной проверки удаляет старый ключ.
## API
Базовый префикс — `/api` (задан `server.servlet.context-path`).
Без токена доступны только `GET /api/photos`, `GET /api/photos/{id}/image`,
`POST /api/auth/login` и `POST /api/auth/register`. Остальные эндпоинты требуют
заголовок `Authorization: Bearer <token>`.

| Метод | Путь | Доступ | Описание |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | все | Регистрация. Возвращает `201` |
| `POST` | `/api/auth/login` | все | Вход. Возвращает `{ "token": "...", "username": "..." }` |
| `GET` | `/api/me` | JWT | Текущий пользователь |
| `GET` | `/api/users` | JWT | Список пользователей |
| `GET` | `/api/photos` | все | Лента. Поддерживает фильтр по тегу: `?tag=пейзаж` |
| `GET` | `/api/photos/{id}/image` | все | Байты картинки (backend проксирует S3, ключи не покидают сервер) |
| `POST` | `/api/upload` | JWT | Загрузка фото (`multipart/form-data`, поле `file`) и тегов (поле `tags`) |
| `GET` | `/api/photos/{id}/likes` | JWT | Список пользователей, поставивших лайк |
| `POST` | `/api/photos/{id}/like` | JWT | Поставить лайк |
| `DELETE` | `/api/photos/{id}/like` | JWT | Снять лайк |
| `PUT` | `/api/photos/{id}/tags` | JWT | Заменить список тегов фото |
Теги нормализуются: ведущий `#` отбрасывается, пробелы обрезаются, дубликаты
сводятся регистронезависимо.
Примеры:
```bash
TOKEN=$(curl -s -X POST http://localhost:8082/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"secret"}' | jq -r .token)
# лента с фильтром по тегу
curl -s 'http://localhost:8082/api/photos?tag=пейзаж' | jq
# загрузка фото
curl -X POST http://localhost:8082/api/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/photo.jpg" -F "tags=пейзаж,город"
# кто поставил лайк
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/photos/1/likes | jq
```
## Разработка
Backend:
```bash
cd backend
./mvnw -B -DskipTests package   # сборка
./mvnw test                     # тесты (нужна живая MariaDB и переменные окружения)
```
Frontend:
```bash
cd frontend
npm run dev     # Vite на :5173, проксирует /api -> http://localhost:8082
npm run build   # сборка в dist/
```
При локальном запуске backend вне Docker нужны переменные окружения
(`MYSQL_*`, `S3_*`, `JWT_SECRET`) — иначе старт упадёт на нерезолвленных плейсхолдерах.
## Структура
```
backend/    Spring Boot API (web/, service/, domain/)
frontend/   Vue SPA (components/, views/, router/)
scripts/    rotate-s3-key.sh — ротация ключа доступа к S3
docker-compose.yaml   весь стек: mariadb, garage, backend, frontend
ts.txt                исходная спецификация
```
