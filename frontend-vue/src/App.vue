<script setup>
import {ref, onMounted} from 'vue'
import axios from "axios";

const API_BASE_URL = 'http://localhost:8080/api'

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
    showToast('Failed to connect to Inventory Service', 'error')
  }
}

// Метод оформления заказа через Gateway -> Order Service
const buyProduct = async (product) => {
  product.loading = true
  try {
    const orderPayload = {
      orderLineItemsList: [
        {
          skuCode: product.skuCode,
          price: product.price,
          quantity: 1
        }
      ]
    }

    // Делаем POST запрос на создание заказа
    const response = await axios.post(`${API_BASE_URL}/order`, orderPayload)

    showToast(response.data, 'success')

    // После успешного заказа обновляем остатки на складе
    await checkStockStatus()
  } catch (error) {
    console.error('Order creation failed:', error)
    const errorMsg = error.response?.data?.message || 'Product is not in stock!'
    showToast(errorMsg, 'error')
  } finally {
    product.loading = false
  }
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
})
</script>

<template>
  <div class="app-container">
    <header class="header">
      <h1>⚡ Microservice Tech Store</h1>
      <p class="subtitle">Full-Stack Demo Application (Spring Boot + Vue 3)</p>
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
              @click="buyProduct(product)"
              :disabled="!product.inStock || product.loading"
              class="buy-button"
          >
            <span v-if="product.loading" class="spinner"></span>
            <span v-else>Buy Now</span>
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
/* Базовые стили */
.app-container {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  color: #2c3e50;
  background-color: #f8f9fa;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
  box-sizing: border-box;
}

.header {
  text-align: center;
  margin-bottom: 3rem;
}

.header h1 {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
  color: #1a252f;
}

.subtitle {
  color: #7f8c8d;
  font-size: 1.1rem;
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
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
  border: 1px solid #eef2f3;
}

.product-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
}

.product-emoji {
  font-size: 4rem;
  margin-bottom: 1rem;
}

.product-name {
  font-size: 1.5rem;
  margin: 0.5rem 0;
  color: #2c3e50;
}

.product-sku {
  font-size: 0.9rem;
  color: #95a5a6;
  margin-bottom: 1rem;
}

.product-sku code {
  background: #f1f2f6;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
}

.product-price {
  font-size: 1.8rem;
  font-weight: bold;
  color: #2e7d32;
  margin-bottom: 1rem;
}

/* Статусы склада */
.stock-badge {
  font-size: 0.9rem;
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

/* Кнопка покупки */
.buy-button {
  width: 100%;
  padding: 0.8rem;
  border: none;
  border-radius: 8px;
  background-color: #1565c0;
  color: white;
  font-size: 1.1rem;
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

/* Анимация загрузки (Spinner) */
.spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 3px solid rgba(255, 255, 255, .3);
  border-radius: 50%;
  border-top-color: #fff;
  animation: spin 1s ease-in-out infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* Всплывающее уведомление (Toast) */
.toast {
  position: fixed;
  bottom: 2rem;
  right: 2rem;
  display: flex;
  align-items: center;
  padding: 1rem 1.5rem;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  background: white;
  z-index: 1000;
  max-width: 400px;
}

.toast.success {
  border-left: 6px solid #2e7d32;
}

.toast.error {
  border-left: 6px solid #c62828;
}

.toast-icon {
  font-size: 1.5rem;
  margin-right: 1rem;
}

.toast-message {
  margin: 0;
  font-weight: 500;
}

/* Анимация для Toast */
.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.2s cubic-bezier(1, 0.5, 0.8, 1);
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateX(20px);
  opacity: 0;
}
</style>