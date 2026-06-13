<script setup>
import {ref, onMounted} from 'vue'
import axios from "axios";

const API_BASE_URL = 'http://localhost:8080/api'

// Данные пользователя и авторизация
const currentUser = ref(localStorage.getItem('username') || null)
const token = ref(localStorage.getItem('token') || null)
const isAdmin = ref(false)

// Состояние корзины
const cart = ref({items: []})
const cartLoading = ref(false)

// Состояние форм авторизации
const authMode = ref('login')
const usernameInput = ref('')
const passwordInput = ref('')
const emailInput = ref('')
const roleInput = ref('USER')
const authLoading = ref(false)

// Состояние панели администратора (добавление товаров на склад)
const adminSkuInput = ref('iphone_15')
const adminQtyInput = ref(10)
const adminLoading = ref(false)

// Список товаров (данные храним на фронт, остатки проверяем на бэке)
const products = ref([
  {
    id: 1,
    name: 'iPhone 15',
    skuCode: 'iphone_15',
    price: 1200,
    emoji: '📱',
    inStock: false,
    quantity: 0,
    loading: false
  },
  {
    id: 2,
    name: 'Google Pixel 8',
    skuCode: 'pixel_8',
    price: 800,
    emoji: '🤖',
    inStock: false,
    quantity: 0,
    loading: false
  }
])

// Состояние всплывающего уведомления (Toast)
const toast = ref({
  show: false,
  message: '',
  type: 'success'
})

// JS-декодер JWT токенов
const decodeJwt = (tokenStr) => {
  try {
    const base64Url = tokenStr.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace('/_/g', '/')
    const jsonPayload = decodeURIComponent(atob(base64).split('').map(function (c) {
      return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
    }).join(''))
    return JSON.parse(jsonPayload)
  } catch (e) {
    return null
  }
}

// Проверка роли пользователя на основе токена
const checkUserRoles = () => {
  if (token.value) {
    const decoded = decodeJwt(token.value)
    if (decoded && decoded.roles) {
      isAdmin.value = decoded.roles.includes('ADMIN')
    } else {
      isAdmin.value = false
    }
  } else {
    isAdmin.value = false
  }
}

// Метод проверки наличия товаров на складе через Gateway -> Inventory Service
const checkStockStatus = async () => {
  try {
    const skuCodes = products.value.map(p => p.skuCode)

    // Делаем GET запрос на http://localhost:8080/api/inventory?skuCode=iphone_15&skuCode=pixel_8
    const response = await axios.get(`${API_BASE_URL}/inventory`, {
      params: {skuCode: skuCodes}
    })

    // Обновляем статус наличия у локального списка товаров
    response.data.forEach(item => {
      const product = products.value.find(p => p.skuCode === item.skuCode)
      if (product) {
        product.inStock = item.isInStock
        product.quantity = item.quantity
      }
    })
  } catch (error) {
    console.error('Failed to fetch stock status:', error)
  }
}

// Загрузка корзины пользователя из Redis
const fetchCart = async () => {
  if (!token.value) return
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    const response = await axios.get(`${API_BASE_URL}/cart`, {headers})
    cart.value = response.data
  } catch (error) {
    console.error('Failed to fetch cart:', error)
  }
}

// Регистрация
const handleRegister = async () => {
  if (!usernameInput.value || !passwordInput.value) {
    showToast('Please fill in username and password', 'error')
    return
  }
  authLoading.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/user/register`, {
      username: usernameInput.value,
      password: passwordInput.value,
      email: emailInput.value,
      role: roleInput.value
    })
    showToast(response.data, 'success')
    authMode.value = 'login'
    passwordInput.value = ''
  } catch (error) {
    showToast(error.response?.data?.message || 'Registration failed', 'error')
  } finally {
    authLoading.value = false
  }
}

// Авторизация
const handleLogin = async () => {
  if (!usernameInput.value || !passwordInput.value) {
    showToast('Please fill in all fields', 'error')
    return
  }
  authLoading.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/user/login`,
        {
          username: usernameInput.value,
          password: passwordInput.value
        })

    // Сохраняем токен в localStorage
    token.value = response.data.token
    currentUser.value = usernameInput.value
    localStorage.setItem('token', response.data.token)
    localStorage.setItem('username', usernameInput.value)

    showToast('Successfully logged in!', 'success')

    // Проверяем роль вошедшего пользователя
    checkUserRoles()

    // Загружаем корзину вошедшего пользователя
    await fetchCart()

    // Очистка полей ввода
    usernameInput.value = ''
    passwordInput.value = ''
  } catch (error) {
    showToast('Invalid username or password', error)
  } finally {
    authLoading.value = false
  }
}

// Выход из аккаунта
const handleLogout = () => {
  token.value = null
  currentUser.value = null
  cart.value = {items: []}
  isAdmin.value = false
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  showToast('Logged out successfully', 'success')
}

// Добавление в корзину с резервированием товара на складе
const addToCart = async (product) => {
  if (!token.value) {
    showToast('Please log in to add items to cart!', 'error')
    return
  }

  product.loading = true
  try {
    const headers = {Authorization: `Bearer ${token.value}`}

    // Делаем запрос к cart-service через POST /api/cart/add?skuCode=...&price=...
    const response = await axios.post(`${API_BASE_URL}/cart/add`, null, {
      params: {
        skuCode: product.skuCode,
        price: product.price
      },
      headers
    })

    cart.value = response.data
    showToast(`Added ${product.name} to cart (Reserved!)`, 'success')

    // Обновляем остатки на складе (уменьшаем на 1)
    await checkStockStatus()
  } catch (error) {
    console.error('Failed to add to cart:', error)
    const errorMsg = error.response?.data?.message || 'Product is not in stock!'
    showToast(errorMsg, 'error')
  } finally {
    product.loading = false
  }
}

// Очистка корзины с возвратом товаров на склад
const clearCart = async () => {
  if (!token.value || cart.value.items.length === 0) return
  cartLoading.value = true
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    await axios.post(`${API_BASE_URL}/cart/clear`, null, {headers})

    cart.value = {items: []}
    showToast('Cart cleared. Items returned to shelves!', 'success')

    // Обновляем остатки на складе
    await checkStockStatus()
  } catch (error) {
    showToast('Failed to clear cart', 'error')
  } finally {
    cartLoading.value = false
  }
}

// Оформление заказа
const checkoutCart = async () => {
  if (!token.value || cart.value.items.length === 0) return
  cartLoading.value = true

  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    const response = await axios.post(`${API_BASE_URL}/cart/checkout`, null, {headers})

    showToast(response.data, 'success')
    cart.value = {items: []}

    await checkStockStatus()
  } catch (error) {
    showToast(error.response?.data?.message || 'Checkout failed', 'error')
  } finally {
    cartLoading.value = false
  }
}

// Административная операция пополнения остатков склада
const addStockToInventory = async () => {
  if (!token.value || !isAdmin.value) return
  adminLoading.value = true
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    const payload = [
      {
        skuCode: adminSkuInput.value,
        quantity: parseInt(adminQtyInput.value)
      }
    ]

    // PUT-запрос к защищенной админской ветке склада через шлюз
    await axios.put(`${API_BASE_URL}/inventory/increase`, payload, {headers})

    showToast(`Successfully added ${adminQtyInput.value} items to ${adminSkuInput.value}!`, 'success')

    // Обновляем остатки на витрине
    await checkStockStatus()
  } catch (error) {
    console.error('Failed to add stock:', error)
    const errorMsg = error.response?.status === 403
        ? 'Access Denied: Only ADMIN can perform this action'
        : 'Failed to update stock'
    showToast(errorMsg, 'error')
  } finally {
    adminLoading.value = false
  }
}

// Подсчет общей стоимости корзины
const getCartTotal = () => {
  return cart.value.items.reduce((total, item) => total + (item.price * item.quantity), 0)
}

// Функция показа уведомления
const showToast = (message, type) => {
  toast.value.message = message
  toast.value.type = type
  toast.value.show = true

  // Автоматически скрываем через 4 секунды
  setTimeout(() => {
    toast.value.show = false
  }, 4000)
}

// Вызываем проверку остатков при загрузке страницы
onMounted(() => {
  checkStockStatus()
  if (token.value) {
    checkUserRoles()
    fetchCart()
  }
})
</script>

<template>
  <div class="app-container">
    <!-- Панель авторизации -->
    <div class="auth-bar">
      <div v-if="currentUser" class="user-profile">
        <span>👤 Welcome, <strong>{{ currentUser }}</strong> <span v-if="isAdmin" class="admin-tag">ADMIN</span></span>
        <button @click="handleLogout" class="logout-button">Logout</button>
      </div>
      <div v-else class="auth-forms">
        <div class="auth-toggle">
          <button
              @click="authMode = 'login'"
              :class="{ active: authMode === 'login' }"
          >Login
          </button>
          <button
              @click="authMode = 'register'"
              :class="{ active: authMode === 'register' }"
          >Register
          </button>
        </div>

        <form @submit.prevent="authMode === 'login' ? handleLogin() : handleRegister()" class="inline-form">
          <input v-model="usernameInput" type="text" placeholder="Username" required/>
          <input v-model="passwordInput" type="password" placeholder="Password" required/>
          <input v-if="authMode === 'register'" v-model="emailInput" type="email" placeholder="Email"/>

          <select v-if="authMode === 'register'" v-model="roleInput" class="role-select">
            <option value="USER">USER</option>
            <option value="ADMIN">ADMIN</option>
          </select>

          <button type="submit" :disabled="authLoading" class="auth-submit">
            {{ authLoading ? '...' : (authMode === 'login' ? 'Sign In' : 'Sign Up') }}
          </button>
        </form>
      </div>
    </div>

    <!-- Панель администратора -->
    <div v-if="isAdmin" class="admin-panel">
      <h2>⚙️Admin Control Panel (Inventory Management)</h2>
      <div class="admin-form">
        <label>Select Product:</label>

        <select v-model="adminSkuInput" class="admin-select">
          <option v-for="product in products"
                  :key="product.id"
                  :value="product.skuCode">{{ product.name }} ({{ product.skuCode }})
          </option>
        </select>

        <label>Quantity to Add:</label>
        <input v-model="adminQtyInput" type="number" min="1" class="admin-qty-input"/>

        <button @click="addStockToInventory" :disabled="adminLoading" class="admin-submit-button">
          {{ adminLoading ? 'Updating...' : 'Add Stock' }}
        </button>
      </div>
    </div>

    <!-- Корзина товаров (показывается только после авторизации) -->
    <div v-if="currentUser" class="cart-panel">
      <h2>🛒 Shopping Cart (Redis Cache)</h2>
      <div v-if="cart.items.length === 0" class="empty-cart">
        Your cart is empty. Reserve some products!
      </div>
      <div v-else class="cart-content">
        <table class="cart-table">
          <thead>
          <tr>
            <th>Product SKU</th>
            <th>Price</th>
            <th>Qty</th>
            <th>Subtotal</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="item in cart.items" :key="item.skuCode">
            <td><code>{{ item.skuCode }}</code></td>
            <td>${{ item.price }}</td>
            <td><strong>{{ item.quantity }}</strong></td>
            <td>${{ item.price * item.quantity }}</td>
          </tr>
          </tbody>
        </table>

        <div class="cart-footer">
          <div class="cart-total">Total: <strong>${{ getCartTotal() }}</strong></div>
          <div class="cart-actions">
            <button @click="clearCart" :disabled="cartLoading" class="clear-button">
              Clear & Return to Shelf
            </button>
            <button @click="checkoutCart" :disabled="cartLoading" class="checkout-button">
              Checkout & Place Order
            </button>
          </div>
        </div>
      </div>
    </div>

    <header class="header">
      <h1>⚡ Microservice Tech Store</h1>
      <p class="subtitle">Distributed Event-Driven Platform with In-Memory Redis Cart & OpenFeign</p>
    </header>

    <main class="main-content">
      <div class="product-grid">
        <div
            v-for="product in products"
            :key="product.id"
            class="product-card"
            :class="{ 'out-of-stock': !product.inStock }"
        >
          <div class="product-emoji">{{ product.emoji }}</div>
          <h2 class="product-name">{{ product.name }}</h2>
          <p class="product-sku">SKU: <code>{{ product.skuCode }}</code></p>
          <p class="product-price">${{ product.price }}</p>

          <div class="stock-badge" :class="product.inStock ? 'in-stock' : 'no-stock'">
            {{ product.inStock ? `● In Stock (${product.quantity} left)` : '❌ Out of Stock' }}
          </div>

          <button
              @click="addToCart(product)"
              :disabled="!product.inStock || product.loading"
              class="buy-button"
          >
            <span v-if="product.loading" class="spinner"></span>
            <span v-else>Add to Cart</span>
          </button>
        </div>
      </div>
    </main>

    <!-- Всплывающее уведомление (Toast) -->
    <transition name="slide-fade">
      <div v-if="toast.show" class="toast" :class="toast.type">
        <span class="toast-icon">{{ toast.type === 'success' ? '✅' : '⚠️' }}</span>
        <p class="toast-message">{{ toast.message }}</p>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.app-container {
  font-family: 'Inter', sans-serif;
  color: #2c3e50;
  background-color: #f8f9fa;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1rem 2rem 2rem 2rem;
  box-sizing: border-box;
}

/* Auth Bar */
.auth-bar {
  width: 100%;
  max-width: 800px;
  background: white;
  padding: 1rem 1.5rem;
  border-radius: 12px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.02);
  margin-bottom: 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-profile {
  display: flex;
  justify-content: space-between;
  width: 100%;
  align-items: center;
}

.logout-button {
  background: #e74c3c;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}

.auth-forms {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  width: 100%;
  flex-wrap: wrap;
}

.auth-toggle {
  display: flex;
  background: #f1f2f6;
  border-radius: 8px;
  padding: 0.2rem;
}

.auth-toggle button {
  border: none;
  background: transparent;
  padding: 0.4rem 1rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  color: #7f8c8d;
}

.auth-toggle button.active {
  background: white;
  color: #2c3e50;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.inline-form {
  display: flex;
  gap: 0.5rem;
  flex-grow: 1;
}

.inline-form input, .role-select {
  padding: 0.5rem;
  border: 1px solid #dcdde1;
  border-radius: 6px;
  outline: none;
}

.inline-form input {
  flex-grow: 1;
}

.role-select {
  background: white;
  cursor: pointer;
}

.auth-submit {
  background: #2c3e50;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}

.admin-tag {
  background: #f1c40f;
  color: #2c3e50;
  font-size: 0.75rem;
  font-weight: bold;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  margin-left: 0.5rem;
}

/* ПАНЕЛЬ АДМИНИСТРАТОРА */
.admin-panel {
  width: 100%;
  max-width: 800px;
  background: #fcfcfc;
  padding: 1.5rem;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.02);
  margin-bottom: 1.5rem;
  border: 1px solid #f1c40f; /* Окрашиваем рамку в золотой цвет */
}

.admin-panel h2 {
  font-size: 1.2rem;
  margin-top: 0;
  margin-bottom: 1.2rem;
  color: #d68910;
}

.admin-form {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.admin-form label {
  font-size: 0.9rem;
  font-weight: 600;
  color: #515a5a;
}

.admin-select {
  padding: 0.5rem;
  border: 1px solid #dcdde1;
  border-radius: 6px;
  background: white;
  outline: none;
}

.admin-qty-input {
  padding: 0.5rem;
  border: 1px solid #dcdde1;
  border-radius: 6px;
  width: 80px;
  outline: none;
}

.admin-submit-button {
  background: #d68910;
  color: white;
  border: none;
  padding: 0.6rem 1.2rem;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
  transition: background-color 0.2s;
}

.admin-submit-button:hover {
  background: #b7730e;
}

.admin-submit-button:disabled {
  background: #b0bec5;
  cursor: not-allowed;
}

/* Панель корзины */
.cart-panel {
  width: 100%;
  max-width: 800px;
  background: white;
  padding: 1.5rem;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.03);
  margin-bottom: 2rem;
  border: 1px solid #eef2f3;
}

.cart-panel h2 {
  font-size: 1.2rem;
  margin-top: 0;
  margin-bottom: 1rem;
  color: #2c3e50;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.empty-cart {
  color: #7f8c8d;
  text-align: center;
  padding: 1.5rem;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px dashed #dcdde1;
}

.cart-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 1.5rem;
  text-align: left;
}

.cart-table th {
  padding: 0.8rem;
  border-bottom: 2px solid #f1f2f6;
  color: #7f8c8d;
  font-size: 0.85rem;
}

.cart-table td {
  padding: 0.8rem;
  border-bottom: 1px solid #f1f2f6;
}

.cart-table code {
  background: #f1f2f6;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
}

.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.cart-total {
  font-size: 1.1rem;
  color: #2c3e50;
}

.cart-total strong {
  color: #2e7d32;
  font-size: 1.4rem;
}

.cart-actions {
  display: flex;
  gap: 0.5rem;
}

.clear-button {
  background: #7f8c8d;
  color: white;
  border: none;
  padding: 0.7rem 1.2rem;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
  transition: background-color 0.2s;
}

.clear-button:hover {
  background: #636e72;
}

.checkout-button {
  background: #2e7d32;
  color: white;
  border: none;
  padding: 0.7rem 1.2rem;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
  transition: background-color 0.2s;
}

.checkout-button:hover {
  background: #1b5e20;
}

.cart-actions button:disabled {
  background: #b0bec5;
  cursor: not-allowed;
}

/* Стилизация заголовка */
.header {
  text-align: center;
  margin-bottom: 2rem;
}

.header h1 {
  font-size: 2rem;
  margin-bottom: 0.5rem;
  color: #1a252f;
}

.subtitle {
  color: #7f8c8d;
  font-size: 0.95rem;
}

/* Сетка товаров */
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 2rem;
  width: 100%;
  max-width: 800px;
}

.product-card {
  background: white;
  border-radius: 16px;
  padding: 2rem;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid #eef2f3;
}

.product-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.06);
}

.product-emoji {
  font-size: 3.5rem;
  margin-bottom: 0.5rem;
}

.product-name {
  font-size: 1.4rem;
  margin: 0.5rem 0;
  color: #2c3e50;
}

.product-sku {
  font-size: 0.85rem;
  color: #95a5a6;
  margin-bottom: 1rem;
}

.product-sku code {
  background: #f1f2f6;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
}

.product-price {
  font-size: 1.6rem;
  font-weight: bold;
  color: #2e7d32;
  margin-bottom: 1rem;
}

.stock-badge {
  font-size: 0.85rem;
  font-weight: 600;
  padding: 0.4rem 1rem;
  border-radius: 50px;
  margin-bottom: 1.5rem;
}

.stock-badge.in-stock {
  background-color: #e8f5e9;
  color: #2e7d32;
}

.stock-badge.no-stock {
  background-color: #ffebee;
  color: #c62828;
}

.buy-button {
  width: 100%;
  padding: 0.8rem;
  border: none;
  border-radius: 8px;
  background-color: #1565c0;
  color: white;
  font-size: 1rem;
  font-weight: bold;
  cursor: pointer;
  transition: background-color 0.2s;
}

.buy-button:hover:not(:disabled) {
  background-color: #0d47a1;
}

.buy-button:disabled {
  background-color: #b0bec5;
  cursor: not-allowed;
}

.spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, .3);
  border-radius: 50%;
  border-top-color: #fff;
  animation: spin 1s ease-in-out infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* Уведомление */
.toast {
  position: fixed;
  bottom: 2rem;
  right: 2rem;
  display: flex;
  align-items: center;
  padding: 1rem 1.5rem;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12);
  background: white;
  z-index: 1000;
}

.toast.success {
  border-left: 6px solid #2e7d32;
}

.toast.error {
  border-left: 6px solid #c62828;
}

.toast-icon {
  font-size: 1.3rem;
  margin-right: 0.8rem;
}

.toast-message {
  margin: 0;
  font-weight: 500;
}

.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.2s cubic-bezier(1, 0.5, 0.8, 1);
}

.slide-fade-enter-from, .slide-fade-leave-to {
  transform: translateX(20px);
  opacity: 0;
}
</style>