<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { currentUser, authHeaders, logout } from '../auth'

const router = useRouter()

const file = ref(null)
const tagsInput = ref('')
const preview = ref(null)
const error = ref(null)
const busy = ref(false)

function onFileChange(e) {
  const f = e.target.files?.[0] ?? null
  file.value = f
  if (preview.value) {
    URL.revokeObjectURL(preview.value)
    preview.value = null
  }
  if (f) preview.value = URL.createObjectURL(f)
}

function parseTags(value) {
  return value
    .split(/[\s,]+/)
    .map((t) => t.trim().replace(/^#+/, ''))
    .filter(Boolean)
}

async function onSubmit() {
  error.value = null

  if (!file.value) {
    error.value = 'Выберите файл'
    return
  }

  busy.value = true
  try {
    const form = new FormData()
    form.append('file', file.value)
    for (const tag of parseTags(tagsInput.value)) {
      form.append('tags', tag)
    }

    const res = await fetch('/api/upload', {
      method: 'POST',
      headers: authHeaders(),
      body: form
    })
    if (res.status === 401) {
      logout()
      router.push('/login')
      return
    }
    if (!res.ok) throw new Error('HTTP ' + res.status)

    router.push('/photos')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="upload">
    <h1>Новый пост</h1>

    <form class="card" @submit.prevent="onSubmit">
      <label>
        Фотография
        <input type="file" accept="image/*" @change="onFileChange" />
      </label>

      <img v-if="preview" class="preview" :src="preview" alt="предпросмотр" />

      <label>
        Хэштеги
        <input v-model="tagsInput" placeholder="через пробел или запятую: природа лето" />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button type="submit" :disabled="busy || !currentUser">
        {{ busy ? 'Публикуем…' : 'Опубликовать' }}
      </button>
      <p v-if="!currentUser" class="hint">Нужно войти, чтобы публиковать.</p>
    </form>
  </section>
</template>

<style scoped>
.upload {
  max-width: 420px;
  margin: 30px auto 0;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  border: 1px solid #e2e2e2;
  border-radius: 10px;
  background: #fff;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
  color: #444;
}
input {
  font: inherit;
}
input[type='text'],
input:not([type]) {
  padding: 8px 10px;
  border: 1px solid #ccc;
  border-radius: 6px;
}
.preview {
  width: 100%;
  border-radius: 8px;
  background: #f2f2f2;
  object-fit: cover;
}
button {
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font: inherit;
}
button:hover:not(:disabled) {
  background: #f0f0f0;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
.error {
  margin: 0;
  color: #c0392b;
  font-size: 13px;
}
.hint {
  margin: 0;
  color: #888;
  font-size: 13px;
}
</style>
