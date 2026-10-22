<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { currentUser, authHeaders, logout } from '../auth'

const router = useRouter()
const route = useRoute()

const searchInput = ref(route.query.tag ? `#${route.query.tag}` : '')

const photos = ref([])
const loading = ref(true)
const error = ref(null)
const pendingLike = ref(new Set())
const likersOpen = ref(new Set())
const likersLoading = ref(new Set())
const likers = ref({})
const editingTags = ref(new Set())
const tagsDraft = ref({})
const savingTags = ref(new Set())

function isOwner(photo) {
  return Boolean(currentUser.value && currentUser.value.username === photo.owner)
}

function parseTags(value) {
  return value
    .split(/[\s,]+/)
    .map((t) => t.trim().replace(/^#+/, ''))
    .filter(Boolean)
}

function startEditTags(photo) {
  tagsDraft.value[photo.id] = (photo.tags ?? []).join(' ')
  editingTags.value.add(photo.id)
}

function cancelEditTags(photo) {
  editingTags.value.delete(photo.id)
  delete tagsDraft.value[photo.id]
}

// Теги своих фотографий: PUT /photos/{id}/tags
async function saveTags(photo) {
  if (savingTags.value.has(photo.id)) return
  savingTags.value.add(photo.id)
  try {
    const res = await fetch(`/api/photos/${photo.id}/tags`, {
      method: 'PUT',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify(parseTags(tagsDraft.value[photo.id] ?? ''))
    })
    if (res.status === 401) {
      logout()
      router.push('/login')
      return
    }
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const updated = await res.json()
    photo.tags = updated.tags
    cancelEditTags(photo)
  } catch (e) {
    error.value = e.message
  } finally {
    savingTags.value.delete(photo.id)
  }
}

function formatDate(value) {
  if (!value) return ''
  return new Date(value).toLocaleString('ru-RU')
}

// URL ленты: при наличии tag в query — фильтр по тегу
function photosUrl() {
  const tag = route.query.tag
  return tag ? `/api/photos?tag=${encodeURIComponent(tag)}` : '/api/photos'
}

async function fetchPhotos() {
  let res = await fetch(photosUrl(), { headers: authHeaders() })
  // токен мог протухнуть — сбрасываем и грузим ленту как гость
  if (res.status === 401) {
    logout()
    res = await fetch(photosUrl())
  }
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json()
}

async function loadPhotos() {
  loading.value = true
  error.value = null
  try {
    photos.value = await fetchPhotos()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

// Поиск выполняется только по кнопке: обновляем query в URL, а watcher перезагружает ленту
function submitSearch() {
  const tag = searchInput.value.trim().replace(/^#+/, '').trim()
  router.push({ path: '/photos', query: tag ? { tag } : {} })
}

onMounted(loadPhotos)
watch(() => route.query.tag, (tag) => {
  searchInput.value = tag ? `#${tag}` : ''
  loadPhotos()
})

// Список лайкнувших доступен только авторизованным пользователям
async function toggleLikers(photo) {
  if (likersOpen.value.has(photo.id)) {
    likersOpen.value.delete(photo.id)
    return
  }
  likersOpen.value.add(photo.id)
  if (likers.value[photo.id]) return

  likersLoading.value.add(photo.id)
  try {
    const res = await fetch(`/api/photos/${photo.id}/likes`, { headers: authHeaders() })
    if (res.status === 401) {
      logout()
      router.push('/login')
      return
    }
    if (!res.ok) throw new Error('HTTP ' + res.status)
    likers.value[photo.id] = await res.json()
  } catch (e) {
    error.value = e.message
  } finally {
    likersLoading.value.delete(photo.id)
  }
}

async function toggleLike(photo) {
  if (!currentUser.value) {
    router.push('/login')
    return
  }
  if (pendingLike.value.has(photo.id)) return

  pendingLike.value.add(photo.id)
  try {
    const method = photo.liked ? 'DELETE' : 'POST'
    const res = await fetch(`/api/photos/${photo.id}/like`, {
      method,
      headers: authHeaders()
    })
    if (res.status === 401) {
      logout()
      router.push('/login')
      return
    }
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const updated = await res.json()
    photo.liked = updated.liked
    photo.likeCount = updated.likeCount
  } catch (e) {
    error.value = e.message
  } finally {
    pendingLike.value.delete(photo.id)
  }
}
</script>

<template>
  <h1>Лента</h1>

  <form class="search" @submit.prevent="submitSearch">
    <input v-model="searchInput" type="text" placeholder="Поиск по тегу, например #природа" />
    <button type="submit">Найти</button>
    <button v-if="route.query.tag" type="button" class="clear" @click="searchInput = ''; submitSearch()">
      Сбросить
    </button>
  </form>

  <p v-if="route.query.tag" class="state">Результаты по тегу #{{ route.query.tag }}</p>

  <p v-if="loading" class="state">Загрузка…</p>
  <p v-else-if="error" class="state error">Ошибка: {{ error }}</p>
  <p v-else-if="photos.length === 0" class="state">Пока пусто</p>

  <div v-else class="feed">
    <article v-for="photo in photos" :key="photo.id" class="card">
      <img class="photo" :src="photo.imageUrl" :alt="photo.fileName" loading="lazy" />
      <div class="meta">
        <div class="row">
          <strong> Автор: {{ photo.owner ?? '—' }}</strong>
          <button
            type="button"
            class="like"
            :class="{ active: photo.liked }"
            :disabled="pendingLike.has(photo.id)"
            :title="currentUser ? 'Поставить/снять лайк' : 'Войдите, чтобы лайкать'"
            @click="toggleLike(photo)"
          >
            ♥ {{ photo.likeCount }}
          </button>
        </div>
        <div class="row muted">
          <span>Название: {{ photo.fileName }}</span>
          <span>Дата публикации: {{ formatDate(photo.createdAt) }}</span>
        </div>
        <div v-if="photo.tags?.length || isOwner(photo)" class="tags-block">
          <template v-if="!editingTags.has(photo.id)">
            <div class="tags">
              <span v-for="tag in photo.tags" :key="tag" class="tag">#{{ tag }}</span>
              <span v-if="!photo.tags?.length" class="muted">Без тегов</span>
            </div>
            <button v-if="isOwner(photo)" type="button" class="link" @click="startEditTags(photo)">
              Изменить теги
            </button>
          </template>
          <form v-else class="tag-editor" @submit.prevent="saveTags(photo)">
            <input v-model="tagsDraft[photo.id]" placeholder="теги через пробел или запятую" />
            <div class="tag-editor-actions">
              <button type="submit" :disabled="savingTags.has(photo.id)">
                {{ savingTags.has(photo.id) ? 'Сохраняем…' : 'Сохранить' }}
              </button>
              <button type="button" @click="cancelEditTags(photo)">Отмена</button>
            </div>
          </form>
        </div>
        <div v-if="currentUser && photo.likeCount > 0">
          <button type="button" class="likers-toggle" @click="toggleLikers(photo)">
            {{ likersOpen.has(photo.id) ? 'Скрыть, кто лайкнул' : 'Кто лайкнул' }}
          </button>
          <div v-if="likersOpen.has(photo.id)" class="likers">
            <span v-if="likersLoading.has(photo.id)" class="muted">Загрузка…</span>
            <span v-for="name in likers[photo.id]" :key="name" class="liker">@{{ name }}</span>
          </div>
        </div>
      </div>
    </article>
  </div>
</template>

<style scoped>
.search {
  display: flex;
  gap: 8px;
  margin: 0 0 16px;
  flex-wrap: wrap;
}
.search input {
  flex: 1 1 240px;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #ccc;
  border-radius: 6px;
  font: inherit;
}
.search button {
  padding: 8px 14px;
  border: 1px solid #ccc;
  border-radius: 6px;
  background: #fff;
  font: inherit;
  cursor: pointer;
}
.search button:hover {
  background: #f0f0f0;
}
.state {
  color: #666;
}
.error {
  color: #c0392b;
}
.feed {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
}
.card {
  border: 1px solid #e2e2e2;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}
.photo {
  display: block;
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: contain;
  object-position: center;
  background: #e9e9e9;
}
.meta {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: baseline;
}
.muted {
  color: #888;
  font-size: 12px;
}
.like {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border: 1px solid #e0245e;
  border-radius: 999px;
  background: #fff;
  color: #e0245e;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.like:hover:not(:disabled) {
  background: #fdeef3;
}
.like.active {
  background: #e0245e;
  color: #fff;
}
.like:disabled {
  opacity: 0.6;
  cursor: default;
}
.likers-toggle {
  padding: 0;
  border: none;
  background: none;
  color: #1d7fd6;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
}
.likers {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}
.liker {
  font-size: 12px;
  color: #e0245e;
}
.tags-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.tag {
  color: #1d7fd6;
  font-size: 13px;
}
.link {
  padding: 0;
  border: none;
  background: none;
  color: #1d7fd6;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
}
.tag-editor {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  flex-wrap: wrap;
}
.tag-editor input {
  flex: 1 1 160px;
  min-width: 0;
  padding: 6px 8px;
  border: 1px solid #ccc;
  border-radius: 6px;
  font: inherit;
  font-size: 13px;
}
.tag-editor-actions {
  display: flex;
  gap: 6px;
}
.tag-editor-actions button {
  padding: 5px 10px;
  border: 1px solid #ccc;
  border-radius: 6px;
  background: #fff;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.tag-editor-actions button:hover:not(:disabled) {
  background: #f0f0f0;
}
.tag-editor-actions button:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
