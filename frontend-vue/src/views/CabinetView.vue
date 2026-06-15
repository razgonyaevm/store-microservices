<script setup>
import {ref, onMounted} from 'vue'
import axios from 'axios'
import {currentUser, token, checkUserRoles} from "../store/auth"

const API_BASE_URL = 'http://localhost:8080/api'

const userBalance = ref(0)
const rechargeAmount = ref(100)
const rechargeLoading = ref(false)

const cart = ref({items: []})
const cartLoading = ref(false)

const authMode = ref('login')
const usernameInput = ref('')
const passwordInput = ref('')
const emailInput = ref('')
const roleInput = ref('USER')
const authLoading = ref(false)

const toast = ref({show: false, message: '', type: 'success'})

const fetchBalance = async () => {
  if (!token.value) return
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    const response = await axios.get(`${API_BASE_URL}/user/balance`, {headers})
    userBalance.value = response.data
  } catch (error) {
    console.error('Failed to fetch balance:', error)
  }
}

const handleRecharge = async () => {
  if (!token.value || rechargeAmount.value <= 0) return
  rechargeLoading.value = true
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    const response = await axios.post(`${API_BASE_URL}/user/recharge`, null, {
      params: {amount: rechargeAmount.value},
      headers
    })
    userBalance.value = response.data
    showToast(`Successfully recharged $${rechargeAmount.value}!`, 'success')
  } catch (error) {
    showToast('Failed to recharge balance', 'error')
  } finally {
    rechargeLoading.value = false
  }
}

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

const handleLogin = async () => {
  if (!usernameInput.value || !passwordInput.value) {
    showToast('Please fill in all fields', 'error')
    return
  }
  authLoading.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/user/login`, {
      username: usernameInput.value,
      password: passwordInput.value
    })

    token.value = response.data.token
    currentUser.value = usernameInput.value

    localStorage.setItem('token', response.data.token)
    localStorage.setItem('username', usernameInput.value)

    showToast('Successfully logged in!', 'success')

    checkUserRoles()
    await fetchBalance()
    await fetchCart()

    usernameInput.value = ''
    passwordInput.value = ''
  } catch (error) {
    showToast('Invalid username or password', 'error')
  } finally {
    authLoading.value = false
  }
}

const removeOneFromCart = async (skuCode) => {
  if (!token.value) return
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    const response = await axios.post(`${API_BASE_URL}/cart/remove`, null, {
      params: {skuCode},
      headers
    })
    cart.value = response.data
    showToast('Returned 1 item to shelf', 'success')
  } catch (error) {
    showToast('Failed to remove item', 'error')
  }
}

const clearCart = async () => {
  if (!token.value || cart.value.items.length === 0) return
  cartLoading.value = true
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    await axios.post(`${API_BASE_URL}/cart/clear`, null, {headers})
    cart.value = {items: []}
    showToast('Cart cleared. Items returned to shelves!', 'success')
  } catch (error) {
    showToast('Failed to clear cart', 'error')
  } finally {
    cartLoading.value = false
  }
}

const checkoutCart = async () => {
  if (!token.value || cart.value.items.length === 0) return
  cartLoading.value = true
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    const response = await axios.post(`${API_BASE_URL}/cart/checkout`, null, {headers})
    showToast(response.data, 'success')
    cart.value = {items: []}
    await fetchBalance()
  } catch (error) {
    showToast(error.response?.data || 'Checkout failed', 'error')
  } finally {
    cartLoading.value = false
  }
}

const getCartTotal = () => {
  return cart.value.items.reduce((total, item) => total + (item.price * item.quantity), 0)
}

const getCartCount = () => {
  return cart.value.items.reduce((total, item) => total + item.quantity, 0)
}

const showToast = (message, type) => {
  toast.value.message = message
  toast.value.type = type
  toast.value.show = true
  setTimeout(() => {
    toast.value.show = false
  }, 4000)
}

onMounted(() => {
  if (token.value) {
    fetchBalance()
    fetchCart()
  }
})
</script>

<template>
  <div class="view-container" style="width: 100%; max-width: 800px;">

    <!-- Первый сценарий, когда пользователь не авторизован, показываем форму логина -->
    <div v-if="!currentUser" class="login-page-card">
      <div class="auth-bar-vertical">
        <h2>🔑 {{ authMode === 'login' ? 'Sign In to Your Account' : 'Create New Account' }}</h2>
        <div class="auth-toggle" style="margin-bottom: 1.5rem;">
          <button @click="authMode = 'login'" :class="{ active: authMode === 'login' }">Login Mode</button>
          <button @click="authMode = 'register'" :class="{ active: authMode === 'register' }">Register Mode</button>
        </div>

        <form @submit.prevent="authMode === 'login' ? handleLogin() : handleRegister()" class="vertical-form">
          <div class="form-group">
            <label>Username</label>
            <input v-model="usernameInput" type="text" placeholder="Enter your username" required
                   class="admin-input-full"/>
          </div>
          <div class="form-group">
            <label>Password</label>
            <input v-model="passwordInput" type="password" placeholder="Enter password" required
                   class="admin-input-full"/>
          </div>
          <div v-if="authMode === 'register'" class="form-group">
            <label>Email Address</label>
            <input v-model="emailInput" type="email" placeholder="e.g. max@mail.com" class="admin-input-full"/>
          </div>

          <div v-if="authMode === 'register'" class="form-group">
            <label>Select Your Role</label>
            <select v-model="roleInput" class="admin-select-full">
              <option value="USER">USER (Buyer)</option>
              <option value="OWNER">OWNER (Store Owner)</option>
            </select>
          </div>

          <button type="submit" :disabled="authLoading" class="auth-submit-large">
            {{ authLoading ? 'Processing...' : (authMode === 'login' ? 'Login' : 'Register Account') }}
          </button>
        </form>
      </div>
    </div>

    <!-- Второй сценарий, когда пользователь вошел в систему, показываем личный кабинет и корзину -->
    <div v-else class="cabinet-layout">
      <!-- Карточка баланса -->
      <div class="balance-card">
        <h2>💳 Personal Wallet & Balance</h2>
        <div class="balance-info-row">
          <div class="balance-amount">
            <span class="label">Current Balance</span>
            <span class="value">${{ userBalance.toFixed(2) }}</span>
          </div>
          <form @submit.prevent="handleRecharge" class="recharge-form-cabinet">
            <div class="form-group">
              <label>Recharge Amount ($)</label>
              <div class="recharge-input-group">
                <input v-model="rechargeAmount" type="number" min="10" step="10" class="admin-input-full"
                       style="width: 100px;" required/>
                <button type="submit" :disabled="rechargeLoading" class="checkout-button">Recharge</button>
              </div>
            </div>
          </form>
        </div>
      </div>

      <!-- Корзина товаров (Redis Cache) -->
      <div class="cart-panel" style="margin-top: 2rem;">
        <h2>🛒 Your Shopping Cart (Redis Cache) <span v-if="getCartCount() > 0"
                                                     class="cart-count-badge">{{ getCartCount() }}</span></h2>
        <div v-if="cart.items.length === 0" class="empty-cart">
          Your cart is empty. Go to Store Catalog and reserve some products!
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
              <td class="cart-qty-cell">
                <button @click="removeOneFromCart(item.skuCode)" class="cart-minus-button"
                        title="Return 1 item to shelf">➖
                </button>
                <strong>{{ item.quantity }}</strong>
              </td>
              <td>${{ item.price * item.quantity }}</td>
            </tr>
            </tbody>
          </table>

          <div class="cart-footer">
            <div class="cart-total">Total: <strong>${{ getCartTotal() }}</strong></div>
            <div class="cart-actions">
              <button @click="clearCart" :disabled="cartLoading" class="clear-button">Clear & Return</button>
              <button @click="checkoutCart" :disabled="cartLoading" class="checkout-button">Place Order</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <transition name="slide-fade">
      <div v-if="toast.show" class="toast" :class="toast.type">
        <span class="toast-icon">{{ toast.type === 'success' ? '✅' : '⚠️' }}</span>
        <p class="toast-message">{{ toast.message }}</p>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.login-page-card {
  background: white;
  padding: 2.5rem;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.03);
  border: 1px solid #eef2f3;
  width: 90%;
}

.vertical-form {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  width: 100%;
  margin-top: 1rem;
}

.auth-submit-large {
  background: #2c3e50;
  color: white;
  border: none;
  padding: 0.8rem;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
  font-size: 1.1rem;
  width: 100%;
  margin-top: 0.5rem;
}

.balance-card {
  background: white;
  padding: 1.5rem;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.03);
  border: 1px solid #eef2f3;
}

.balance-card h2 {
  font-size: 1.2rem;
  margin-top: 0;
  margin-bottom: 1.2rem;
  color: #2e7d32;
}

.balance-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1.5rem;
}

.balance-amount {
  display: flex;
  flex-direction: column;
}

.balance-amount .label {
  font-size: 0.85rem;
  color: #7f8c8d;
  font-weight: 600;
}

.balance-amount .value {
  font-size: 2rem;
  font-weight: bold;
  color: #2e7d32;
}

.recharge-input-group {
  display: flex;
  gap: 0.5rem;
}
</style>