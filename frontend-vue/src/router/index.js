import {createRouter, createWebHistory} from "vue-router"
import CatalogView from '../views/CatalogView.vue'
import CabinetView from '../views/CabinetView.vue'
import AdminView from '../views/AdminView.vue'

const routes = [
    {
        path: '/',
        name: 'catalog',
        component: CatalogView
    },
    {
        path: '/cabinet',
        name: 'cabinet',
        component: CabinetView // Страница кабинета требует авторизации
    },
    {
        path: '/admin',
        name: 'admin',
        component: AdminView,
        meta: {requiresOwner: true}, // Страница требует роли OWNER или ADMIN
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/' // Перенаправляем все несуществующие страницы на главную
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// Проверка авторизации и ролей
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')

    // Декодируем роль из JWT-токена
    let isOwner = false
    if (token) {
        try {
            const base64Url = token.split('.')[1]
            const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
            const decoded = JSON.parse(atob(base64))
            isOwner = decoded.roles && (decoded.roles.includes('OWNER') || decoded.roles.includes('ADMIN'))
        } catch (e) {
            console.error('Failed to parse token in router guard', e)
        }
    }

    // Если страница требует роли OWNER, а пользователь не админ и не владелец, то блокируем переход
    if (to.meta.requiresOwner && !isOwner) {
        next('/')
    } else {
        next()
    }
})

export default router