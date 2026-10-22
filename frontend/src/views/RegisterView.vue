<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register, login } from '../auth'

const router = useRouter()

const username = ref('')
const password = ref('')
const password2 = ref('')
const error = ref(null)
const busy = ref(false)

async function onSubmit() {
  error.value = null

  if (!username.value.trim() || !password.value) {
    error.value = 'Укажите логин и пароль'
    return
  }
  if (password.value !== password2.value) {
    error.value = 'Пароли не совпадают'
    return
  }

  busy.value = true
  try {
    await register(username.value, password.value)
    await login(username.value, password.value)
    router.push('/photos')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="auth">
    <h1>Регистрация</h1>

    <form class="card" @submit.prevent="onSubmit">
      <label>
        Логин
        <input v-model="username" autocomplete="username" />
      </label>
      <label>
        Пароль
        <input v-model="password" type="password" autocomplete="new-password" />
      </label>
      <label>
        Повторите пароль
        <input v-model="password2" type="password" autocomplete="new-password" />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button type="submit" :disabled="busy">{{ busy ? 'Создаём…' : 'Зарегистрироваться' }}</button>
    </form>

    <p class="hint">
      Уже есть аккаунт?
      <RouterLink to="/login">Войти</RouterLink>
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
