<script setup>
import {ref, onMounted} from 'vue'
import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api'
const products = ref([])
const token = ref(localStorage.getItem('token') || null)
const toast = ref({show: false, message: '', type: 'success'})

const fetchProducts = async () => {
  try {
    const response = await axios.get(`${API_BASE_URL}/inventory/products`)
    products.value = response.data
        .sort((a, b) => b.price - a.price)
        .map(p => ({
          ...p,
          inStock: p.quantity > 0,
          loading: false
        }))
  } catch (error) {
    console.error('Failed to fetch products:', error)
  }
}

const addToCart = async (product) => {
  if (!token.value) {
    showToast('Please log in to add items to cart!', 'error')
    return
  }
  product.loading = true
  try {
    const headers = {Authorization: `Bearer ${token.value}`}
    await axios.post(`${API_BASE_URL}/cart/add`, null, {
      params: {skuCode: product.skuCode, price: product.price},
      headers
    })
    showToast(`Added ${product.name} to cart (Reserved!)`, 'success')
    await fetchProducts()
  } catch (error) {
    showToast(error.response?.data?.message || error.response?.data || 'Product is not in stock!', 'error')
  } finally {
    product.loading = false
  }
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
  fetchProducts()
})
</script>

<template>
  <div class="view-container">
    <header class="header">
      <h1>Microservice Tech Store</h1>
      <p class="subtitle">Distributed Event-Driven Platform with In-Memory Redis Cart & Dynamic DB Catalog</p>
    </header>

    <main class="main-content">
      <div class="product-grid">
        <div v-for="product in products" :key="product.id" class="product-card"
             :class="{ 'out-of-stock': !product.inStock }">
          <div class="product-emoji">{{ product.emoji }}</div>
          <h2 class="product-name">{{ product.name }}</h2>
          <p class="product-sku">SKU: <code>{{ product.skuCode }}</code></p>
          <p class="product-price">${{ product.price }}</p>

          <div class="stock-badge" :class="product.inStock ? 'in-stock' : 'no-stock'">
            {{ product.inStock ? `● In Stock (${product.quantity} left)` : '❌ Out of Stock' }}
          </div>

          <button @click="addToCart(product)" :disabled="product.loading || !product.inStock" class="buy-button">
            <span v-if="product.loading" class="spinner"></span>
            <span v-else>Add to Cart</span>
          </button>
        </div>
      </div>
    </main>

    <transition name="slide-fade">
      <div v-if="toast.show" class="toast" :class="toast.type">
        <span class="toast-icon">{{ toast.type === 'success' ? '✅' : '⚠️' }}</span>
        <p class="toast-message">{{ toast.message }}</p>
      </div>
    </transition>
  </div>
</template>