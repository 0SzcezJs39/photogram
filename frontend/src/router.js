import { createRouter, createWebHistory } from 'vue-router'
import PhotosView from './views/PhotosView.vue'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import UploadView from './views/UploadView.vue'
import { hasToken } from './auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/photos' },
    { path: '/photos', component: PhotosView },
    { path: '/login', component: LoginView },
    { path: '/register', component: RegisterView },
    { path: '/upload', component: UploadView, meta: { requiresAuth: true } }
  ]
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !hasToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
