<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RouterLink, RouterView } from 'vue-router'
import { currentUser, logout, loadMe } from './auth'

const router = useRouter()

onMounted(loadMe)

function onLogout() {
  logout()
  router.push('/photos')
}
</script>

<template>
  <header class="topbar">
    <RouterLink to="/photos" class="brand">Фотограм</RouterLink>

    <div v-if="currentUser" class="user">
      <span class="greeting">Привет, {{ currentUser.username }}!</span>
      <RouterLink to="/upload" class="btn">Добавить</RouterLink>
      <button type="button" @click="onLogout">Выйти</button>
    </div>
    <nav v-else class="user">
      <RouterLink to="/login" class="btn">Войти</RouterLink>
      <RouterLink to="/register" class="btn">Регистрация</RouterLink>
    </nav>
  </header>

  <main class="content">
    <RouterView />
  </main>
</template>

<style scoped>
.user {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.greeting {
  font-size: 14px;
  color: #333;
}
.user button {
  padding: 4px 12px;
  border: 1px solid #ccc;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font: inherit;
}
.user button:hover {
  background: #f0f0f0;
}
.btn {
  padding: 4px 12px;
  border: 1px solid #ccc;
  border-radius: 6px;
  background: #fff;
  color: #222;
  text-decoration: none;
  font: inherit;
}
.btn:hover {
  background: #f0f0f0;
}
.btn.router-link-active {
  border-color: #999;
  background: #f0f0f0;
}
</style>
