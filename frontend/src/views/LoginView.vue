<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { login } from '../auth'

const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const error = ref(null)
const busy = ref(false)

async function onSubmit() {
  error.value = null
  busy.value = true
  try {
    await login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/photos'
    router.push(redirect)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="auth">
    <h1>Вход</h1>

    <form class="card" @submit.prevent="onSubmit">
      <label>
        Логин
        <input v-model="username" autocomplete="username" />
      </label>
      <label>
        Пароль
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button type="submit" :disabled="busy">{{ busy ? 'Входим…' : 'Войти' }}</button>
    </form>

    <p class="hint">
      Нет аккаунта?
      <RouterLink to="/register">Зарегистрироваться</RouterLink>
    </p>
  </section>
</template>

<style scoped>
.auth {
  max-width: 340px;
  margin: 40px auto 0;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  border: 1px solid #e2e2e2;
  border-radius: 10px;
  background: #fff;
}
label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  color: #444;
}
input {
  padding: 8px 10px;
  border: 1px solid #ccc;
  border-radius: 6px;
  font: inherit;
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
  font-size: 13px;
  color: #666;
  text-align: center;
}
</style>
