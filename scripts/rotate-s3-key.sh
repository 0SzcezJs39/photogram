#!/usr/bin/env bash
#
# Ротация ключа доступа к S3 (Garage).
#
# Зачем: ключ в .env задаётся вручную и подбирается за секунды
# (например, "GK" + 22 символа "1234567890ABCDEF123456" и секрет из
# последовательных "1234567890abcdef..."). Скрипт выпускает случайный ключ,
# переносит права на бакет, переключает .env и только потом удаляет старый
# ключ — чтобы в процессе ничего не перестало работать.
#
# Запуск (нужен доступ к docker):
#   ./scripts/rotate-s3-key.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=".env"
CONTAINER="photogram_garage_s3"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ОШИБКА: не найден $ENV_FILE (запускать из корня репозитория)" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "ОШИБКА: контейнер $CONTAINER не запущен (docker compose up -d)" >&2
  exit 1
fi

read_env() {
  grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-
}

OLD_ACCESS="$(read_env S3_ACCESS_KEY)"
OLD_SECRET="$(read_env S3_SECRET_KEY)"
BUCKET="$(read_env S3_BUCKET_NAME)"

if [[ -z "$OLD_ACCESS" || -z "$OLD_SECRET" || -z "$BUCKET" ]]; then
  echo "ОШИБКА: в $ENV_FILE нет S3_ACCESS_KEY / S3_SECRET_KEY / S3_BUCKET_NAME" >&2
  exit 1
fi

# Garage: ключ = "GK" + 22 hex-символа, секрет = 40 hex-символов
NEW_ACCESS="GK$(openssl rand -hex 11)"
NEW_SECRET="$(openssl rand -hex 20)"

echo "==> Новый ключ:  $NEW_ACCESS"

echo "==> Импортируем ключ в Garage"
if ! docker exec "$CONTAINER" /garage key import --key-id "$NEW_ACCESS" --yes "$NEW_SECRET"; then
  echo "    --key-id не поддерживается, импортируем по имени"
  docker exec "$CONTAINER" /garage key import -n app-key-rotated --yes "$NEW_SECRET"
  NEW_ACCESS="$(docker exec "$CONTAINER" /garage key info app-key-rotated \
                | grep -oiE 'GK[0-9a-f]{22}' | tail -1)"
  [[ -n "$NEW_ACCESS" ]] || { echo "ОШИБКА: не удалось определить ID нового ключа" >&2; exit 1; }
  echo "    получен ключ $NEW_ACCESS"
fi

echo "==> Выдаём права на бакет $BUCKET"
docker exec "$CONTAINER" /garage bucket allow "$BUCKET" --read --write --key "$NEW_ACCESS"

echo "==> Прописываем новый ключ в $ENV_FILE"
cp "$ENV_FILE" "${ENV_FILE}.bak.$(date +%Y%m%d%H%M%S)"
sed -i "s|^S3_ACCESS_KEY=.*|S3_ACCESS_KEY=$NEW_ACCESS|" "$ENV_FILE"
sed -i "s|^S3_SECRET_KEY=.*|S3_SECRET_KEY=$NEW_SECRET|" "$ENV_FILE"

echo "==> Перезапускаем backend, чтобы он подхватил новый ключ"
docker compose up -d --build backend

echo "==> Проверяем, что лента и картинки отдаются"
sleep 5
curl -sf -o /dev/null http://localhost:8082/api/photos && echo "    /api/photos OK" \
  || { echo "ОШИБКА: backend не отвечает, старый ключ не удалён — откатите .env из .bak" >&2; exit 1; }

echo "==> Удаляем старый ключ $OLD_ACCESS"
if ! docker exec "$CONTAINER" /garage key delete --yes "$OLD_ACCESS"; then
  if ! printf 'yes\n' | docker exec -i "$CONTAINER" /garage key delete "$OLD_ACCESS"; then
    echo "    ВНИМАНИЕ: не удалось удалить старый ключ автоматически."
    echo "    Удалите его вручную: docker exec $CONTAINER /garage key delete $OLD_ACCESS"
  fi
fi

echo
echo "Готово. Новый ключ: $NEW_ACCESS"
echo "Бэкап .env: $(ls -1t ${ENV_FILE}.bak.* | head -1)"
