import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useAuth } from '../stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  {
    path: '/',
    name: 'dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/users',
    name: 'users',
    component: () => import('../views/UsersView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/clients',
    name: 'clients',
    component: () => import('../views/ClientsView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { requiresAuth: true },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const { authenticated, isAdmin, load } = useAuth()

  // 首次进入时拉一次；后续复用缓存状态
  if (!useAuth().state.loaded) {
    await load().catch(() => null)
  }

  if (to.meta.requiresAuth && !authenticated.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !isAdmin.value) {
    return { name: 'dashboard' }
  }

  return true
})
