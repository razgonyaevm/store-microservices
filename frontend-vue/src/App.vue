<script setup>
import {useRouter} from 'vue-router'
import {currentUser, token, isAdmin, validateSession} from './store/auth'
import {onMounted} from "vue";

const router = useRouter()

const handleLogout = () => {
  token.value = null
  currentUser.value = null
  isAdmin.value = false
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/') // Возвращаем пользователя на витрину
}

onMounted(async () => {
  await validateSession()
})
</script>

<template>
  <div class="app-container">
    <!-- Навигационная панель -->
    <nav class="navbar">
      <div class="nav-logo">Micro Tech Store</div>
      <div class="nav-links">
        <router-link to="/" class="nav-link" active-class="active">🛒 Store Catalog</router-link>

        <router-link to="/cabinet" class="nav-link" active-class="active">
          {{ currentUser ? `👤 Profile (${currentUser})` : '🔑 Sign In / Register' }}
        </router-link>

        <router-link v-if="isAdmin" to="/admin" class="nav-link admin-link" active-class="active">
          ⚙️ Admin Panel
        </router-link>

        <button v-if="currentUser" @click="handleLogout" class="navbar-logout-btn">Logout</button>
      </div>
    </nav>

    <router-view/>
  </div>
</template>

<style scoped>
.navbar {
  width: 100%;
  max-width: 800px;
  background: white;
  padding: 1rem 1.5rem;
  border-radius: 12px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.02);
  margin-bottom: 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-sizing: border-box;
}

.nav-logo {
  font-size: 1.2rem;
  font-weight: bold;
  color: #1a252f;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.nav-link {
  text-decoration: none;
  color: #7f8c8d;
  font-weight: 600;
  font-size: 0.95rem;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  transition: all 0.2s;
}

.nav-link:hover, .nav-link.active {
  color: #2c3e50;
  background-color: #f1f2f6;
}

.admin-link.active {
  background-color: #fef9e7;
  color: #d68910;
}

.navbar-logout-btn {
  background: #e74c3c;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.9rem;
}
</style>