import { ref } from 'vue'
import axios from 'axios' // Импортируем axios

const API_BASE_URL = 'http://localhost:8080/api'

export const currentUser = ref(localStorage.getItem('username') || null)
export const token = ref(localStorage.getItem('token') || null)
export const isAdmin = ref(false)
export const isOwner = ref(false)

export const decodeJwt = (tokenStr) => {
    try {
        const base64Url = tokenStr.split('.')[1]
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
        return JSON.parse(atob(base64))
    } catch (e) {
        return null
    }
}

export const checkUserRoles = () => {
    if (token.value) {
        const decoded = decodeJwt(token.value)
        if (decoded && decoded.roles) {
            isAdmin.value = decoded.roles.includes('ADMIN')
            // ADMIN автоматически наследует права OWNER
            isOwner.value = decoded.roles.includes('OWNER') || decoded.roles.includes('ADMIN')
        } else {
            isAdmin.value = false
            isOwner.value = false
        }
    } else {
        isAdmin.value = false
    }
}

// Валидация актуальности сессии и роли
export const validateSession = async () => {
    if (!token.value) return
    try {
        const headers = { Authorization: `Bearer ${token.value}` }
        const response = await axios.get(`${API_BASE_URL}/user/me`, { headers })

        const dbRole = response.data.role // Роль из бд
        const decoded = decodeJwt(token.value)
        const jwtRole = decoded && decoded.roles ? decoded.roles[0] : null // Роль из текущего токена

        // Если роль в бд изменилась и больше не совпадает с ролью в токене
        if (jwtRole && dbRole && jwtRole !== dbRole) {
            // Принудительно разлогиниваем пользователя
            token.value = null
            currentUser.value = null
            isAdmin.value = false
            isOwner.value = false
            localStorage.removeItem('token')
            localStorage.removeItem('username')

            alert('Your account permissions have been updated. Please log in again.')
            window.location.href = '/' // Перенаправляем на главную
        }
    } catch (error) {
        console.error('Session validation failed:', error)
    }
}

// Перехватчик ответов axios, чтобы ловить ошибки 403
axios.interceptors.response.use(
    (response) => {
        return response
    },
    (error) => {
        // Если сервер вернул forbidden что роль изменилась в бд
        if (error.response && error.response.status === 403) {
            // Очищаем сессию в приложении
            token.value = null
            currentUser.value = null
            isAdmin.value = false
            isOwner.value = false
            localStorage.removeItem('token')
            localStorage.removeItem('username')

            alert('Access Denied: Your account permissions have been updated. Please log in again.')
            window.location.href = '/'
        }
        return Promise.reject(error)
    }
)

checkUserRoles()