<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api'
const token = ref(localStorage.getItem('token') || null)

// Вкладка админки: 'inventory' | 'users'
const adminTab = ref('inventory')

// Списки данных
const products = ref([])
const users = ref([])

// Состояния
const adminLoading = ref(false)
const userActionLoading = ref(false)

// Пополнение остатков
const adminSkuInput = ref('')
const adminQtyInput = ref(10)

// Создание нового товара
const newSku = ref('')
const newName = ref('')
const newPrice = ref(100)
const newQty = ref(10)
const newEmoji = ref('📦')

// Редактирование товара
const editProductId = ref('')
const editName = ref('')
const editSku = ref('')
const editPrice = ref(0)
const editQty = ref(0)
const editEmoji = ref('')

const toast = ref({ show: false, message: '', type: 'success' })

// Загрузка товаров
const fetchProducts = async () => {
  try {
    const response = await axios.get(`${API_BASE_URL}/inventory/products`)
    products.value = response.data.map(p => ({
      ...p,
      inStock: p.quantity > 0,
      loading: false
    }))
    if (products.value.length > 0 && !adminSkuInput.value) {
      adminSkuInput.value = products.value[0].skuCode
    }
  } catch (error) {
    console.error(error)
  }
}

// Загрузка всех пользователей из user-service (доступно только админу)
const fetchUsers = async () => {
  if (!token.value) return
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    const response = await axios.get(`${API_BASE_URL}/user/all`, { headers })
    users.value = response.data
  } catch (error) {
    console.error('Failed to fetch users:', error)
    showToast('Failed to load users list from DB', 'error')
  }
}

// Изменение роли пользователя (возможность повышения до админа или понижение)
const changeUserRole = async (userId, newRole) => {
  if (!token.value) return
  userActionLoading.value = true
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    await axios.put(`${API_BASE_URL}/user/${userId}/role`, null, {
      params: { role: newRole },
      headers
    })
    showToast(`Successfully updated user role to ${newRole}!`, 'success')
    await fetchUsers() // Обновляем список пользователей
  } catch (error) {
    showToast('Failed to change user role', 'error')
  } finally {
    userActionLoading.value = false
  }
}

// Удаление пользователя из базы данных
const deleteUser = async (user) => {
  if (!token.value) return
  const currentUsername = localStorage.getItem('username')
  if (user.username === currentUsername) {
    showToast('You cannot delete your own admin account!', 'error')
    return
  }
  if (!confirm(`Are you sure you want to delete user "${user.username}" and all their data?`)) return
  userActionLoading.value = true
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    await axios.delete(`${API_BASE_URL}/user/${user.id}`, { headers })
    showToast(`User "${user.username}" successfully deleted`, 'success')
    await fetchUsers()
  } catch (error) {
    showToast('Failed to delete user', 'error')
  } finally {
    userActionLoading.value = false
  }
}

// Пополнение остатков
const addStockToInventory = async () => {
  if (!token.value) return
  adminLoading.value = true
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    const payload = [{ skuCode: adminSkuInput.value, quantity: parseInt(adminQtyInput.value) }]
    await axios.put(`${API_BASE_URL}/inventory/increase`, payload, { headers })
    showToast('Stock successfully replenished!', 'success')
    await fetchProducts()
  } catch (error) {
    showToast('Failed to add stock', 'error')
  } finally {
    adminLoading.value = false
  }
}

// Создание товара
const createNewProduct = async () => {
  if (!token.value) return
  adminLoading.value = true
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    const payload = {
      skuCode: newSku.value,
      name: newName.value,
      price: parseFloat(newPrice.value),
      quantity: parseInt(newQty.value),
      emoji: newEmoji.value
    }
    await axios.post(`${API_BASE_URL}/inventory/products`, payload, { headers })
    showToast(`Product ${newName.value} created successfully!`, 'success')
    newSku.value = ''
    newName.value = ''
    newPrice.value = 100
    newQty.value = 10
    newEmoji.value = '📦'
    await fetchProducts()
  } catch (error) {
    showToast('Failed to create product', 'error')
  } finally {
    adminLoading.value = false
  }
}

// Автозаполнение при редактировании
const handleEditProductChange = () => {
  const product = products.value.find(p => p.id === editProductId.value)
  if (product) {
    editName.value = product.name
    editSku.value = product.skuCode
    editPrice.value = product.price
    editQty.value = product.quantity
    editEmoji.value = product.emoji
  }
}

// Сохранение изменений
const updateProductDetails = async () => {
  if (!token.value || !editProductId.value) return
  adminLoading.value = true
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    const payload = {
      skuCode: editSku.value,
      name: editName.value,
      price: parseFloat(editPrice.value),
      quantity: parseInt(editQty.value),
      emoji: editEmoji.value
    }
    await axios.put(`${API_BASE_URL}/inventory/products/${editProductId.value}`, payload, { headers })
    showToast('Product updated successfully!', 'success')
    editProductId.value = ''
    await fetchProducts()
  } catch (error) {
    showToast('Failed to update product', 'error')
  } finally {
    adminLoading.value = false
  }
}

// Удаление товара
const handleDeleteProduct = async (productId) => {
  if (!token.value) return
  if (!confirm('Delete this product from DB?')) return
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    await axios.delete(`${API_BASE_URL}/inventory/products/${productId}`, { headers })
    showToast('Product deleted successfully!', 'success')
    await fetchProducts()
  } catch (error) {
    showToast('Failed to delete product', 'error')
  }
}

const showToast = (message, type) => {
  toast.value.message = message
  toast.value.type = type
  toast.value.show = true
  setTimeout(() => { toast.value.show = false }, 4000)
}

onMounted(() => {
  fetchProducts()
  fetchUsers() // Загружаем список пользователей из бд
})
</script>

<template>
  <div class="view-container" style="width: 100%; max-width: 800px;">

    <!-- Переключатель вкладок админки -->
    <div class="auth-toggle" style="margin-bottom: 2rem; justify-content: center;">
      <button @click="adminTab = 'inventory'" :class="{ active: adminTab === 'inventory' }">📦 Inventory & Catalog</button>
      <button @click="adminTab = 'users'" :class="{ active: adminTab === 'users' }">👥 User Management</button>
    </div>

    <!-- Первая вкладка управления складом и каталогом -->
    <div v-if="adminTab === 'inventory'" class="admin-panel">
      <h2>⚙️ Inventory & Catalog Control</h2>
      <div class="admin-sections">

        <!-- Первая секция пополнения остатков -->
        <div class="admin-section">
          <h3>📦 Add Existing Stock</h3>
          <div class="admin-grid-form">
            <div class="form-group">
              <label>Select Product</label>
              <select v-model="adminSkuInput" class="admin-select-full">
                <option v-for="product in products" :key="product.id" :value="product.skuCode">
                  {{ product.name }} ({{ product.skuCode }})
                </option>
              </select>
            </div>
            <div class="form-group">
              <label>Quantity to Add</label>
              <input v-model="adminQtyInput" type="number" min="1" class="admin-input-full" />
            </div>
            <div class="form-group action-group">
              <button @click="addStockToInventory" :disabled="adminLoading" class="admin-submit-button-full">Add Stock</button>
            </div>
          </div>
        </div>

        <!-- Вторая секция создания товара -->
        <div class="admin-section">
          <h3>✨ Create New Product from Scratch</h3>
          <div class="admin-grid-form">
            <div class="form-group">
              <label>Product Name</label>
              <input v-model="newName" type="text" placeholder="e.g. iPad Pro" class="admin-input-full" />
            </div>
            <div class="form-group">
              <label>SKU Code</label>
              <input v-model="newSku" type="text" placeholder="e.g. ipad_pro" class="admin-input-full" />
            </div>
            <div class="form-group">
              <label>Price ($)</label>
              <input v-model="newPrice" type="number" min="1" class="admin-input-full" />
            </div>
            <div class="form-group">
              <label>Initial Qty</label>
              <input v-model="newQty" type="number" min="1" class="admin-input-full" />
            </div>
            <div class="form-group">
              <label>Emoji</label>
              <input v-model="newEmoji" type="text" class="admin-input-full" style="text-align: center;" />
            </div>
            <div class="form-group action-group">
              <button @click="createNewProduct" :disabled="adminLoading" class="admin-submit-button-full">Create Product</button>
            </div>
          </div>
        </div>

        <!-- Третья секция редактирования товара -->
        <div class="admin-section">
          <h3>✏️ Edit Product Details</h3>
          <div class="admin-grid-form">
            <div class="form-group">
              <label>Select Product to Edit</label>
              <select v-model="editProductId" @change="handleEditProductChange" class="admin-select-full">
                <option value="" disabled>-- Choose Product --</option>
                <option v-for="product in products" :key="product.id" :value="product.id">{{ product.name }}</option>
              </select>
            </div>
            <template v-if="editProductId">
              <div class="form-group">
                <label>Name</label>
                <input v-model="editName" type="text" class="admin-input-full" />
              </div>
              <div class="form-group">
                <label>SKU</label>
                <input v-model="editSku" type="text" class="admin-input-full" />
              </div>
              <div class="form-group">
                <label>Price ($)</label>
                <input v-model="editPrice" type="number" min="1" class="admin-input-full" />
              </div>
              <div class="form-group">
                <label>Quantity</label>
                <input v-model="editQty" type="number" min="0" class="admin-input-full" />
              </div>
              <div class="form-group">
                <label>Emoji</label>
                <input v-model="editEmoji" type="text" class="admin-input-full" style="text-align: center;" />
              </div>
              <div class="form-group action-group">
                <button @click="updateProductDetails" :disabled="adminLoading" class="admin-submit-button-full">Save Changes</button>
              </div>
            </template>
          </div>
        </div>

        <!-- Четвертая секция списка товаров с витрины для быстрого удаления -->
        <div class="admin-section">
          <h3>🗑️ Quick Delete Products</h3>
          <table class="cart-table">
            <thead>
            <tr>
              <th>Emoji</th>
              <th>Product Name</th>
              <th>SKU</th>
              <th>Price</th>
              <th>Action</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="product in products" :key="product.id">
              <td style="font-size: 1.5rem;">{{ product.emoji }}</td>
              <td><strong>{{ product.name }}</strong></td>
              <td><code>{{ product.skuCode }}</code></td>
              <td>${{ product.price }}</td>
              <td>
                <button @click="handleDeleteProduct(product.id)" class="logout-button" style="padding: 0.3rem 0.7rem; font-size: 0.85rem;">Delete</button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>

      </div>
    </div>

    <!-- Вторая вкладка управления пользователями -->
    <div v-if="adminTab === 'users'" class="admin-panel" style="border-color: #2c3e50;">
      <h2 style="color: #2c3e50;">👥 System User Management (PostgreSQL Database)</h2>
      <div class="cart-panel" style="border: none; padding: 0; box-shadow: none;">
        <table class="cart-table">
          <thead>
          <tr>
            <th>ID</th>
            <th>Username</th>
            <th>Email</th>
            <th>Role</th>
            <th>Balance</th>
            <th>Actions</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>#{{ user.id }}</td>
            <td><strong>{{ user.username }}</strong></td>
            <td>{{ user.email || 'n/a' }}</td>
            <td>
                <span class="admin-tag" :style="{ background: user.role === 'ADMIN' ? '#f1c40f' : '#b0bec5' }">
                  {{ user.role }}
                </span>
            </td>
            <td style="color: #2e7d32; font-weight: bold;">${{ user.balance.toFixed(2) }}</td>
            <td style="display: flex; gap: 0.3rem;">
              <!-- Кнопки управления ролью пользователя -->
              <button
                  v-if="user.role === 'USER'"
                  @click="changeUserRole(user.id, 'ADMIN')"
                  :disabled="userActionLoading"
                  class="checkout-button"
                  style="padding: 0.3rem 0.6rem; font-size: 0.8rem;"
              >
                Promote to Admin
              </button>
              <button
                  v-else
                  @click="changeUserRole(user.id, 'USER')"
                  :disabled="userActionLoading"
                  class="clear-button"
                  style="padding: 0.3rem 0.6rem; font-size: 0.8rem;"
              >
                Demote to User
              </button>

              <!-- Кнопка удаления пользователя -->
              <button
                  @click="deleteUser(user)"
                  :disabled="userActionLoading"
                  class="logout-button"
                  style="padding: 0.3rem 0.6rem; font-size: 0.8rem;"
              >
                Delete
              </button>
            </td>
          </tr>
          </tbody>
        </table>
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