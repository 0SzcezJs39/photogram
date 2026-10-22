import { ref } from 'vue'

const TOKEN_KEY = 'photogram_token'

export const currentUser = ref(null)

// Есть ли сохранённый токен (для навигационных гардов)
export function hasToken() {
  return Boolean(localStorage.getItem(TOKEN_KEY))
}

// Заголовок Authorization для защищённых запросов (лайк, загрузка, теги)
export function authHeaders() {
  const token = localStorage.getItem(TOKEN_KEY)
  return token ? { Authorization: `Bearer ${token}` } : {}
}

// Логин по паре логин/пароль -> JWT, который кладём в localStorage
export async function login(username, password) {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!res.ok) throw new Error('Неверный логин или пароль')
  const data = await res.json()
  localStorage.setItem(TOKEN_KEY, data.token)
  currentUser.value = { username: data.username }
  return currentUser.value
}

// Регистрация нового пользователя (токен не выдаём, после неё нужно войти)
export async function register(username, password) {
  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    throw new Error(data.error || 'Не удалось зарегистрироваться')
  }
  return res.json()
}

export function logout() {
  localStorage.removeItem(TOKEN_KEY)
  currentUser.value = null
}

// Восстанавливаем пользователя по сохранённому токену
export async function loadMe() {
  if (!localStorage.getItem(TOKEN_KEY)) {
    currentUser.value = null
    return null
  }
  const res = await fetch('/api/me', { headers: authHeaders() })
  if (res.ok) {
    const data = await res.json()
    currentUser.value = { username: data.username }
  } else {
    logout()
  }
  return currentUser.value
}
