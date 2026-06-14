<template>
  <div class="app-container">
    <!-- Панель авторизации -->
    <div class="auth-bar">
      <div v-if="currentUser" class="user-profile">
        <div class="profile-info">
          <span>👤 Welcome, <strong>{{ currentUser }}</strong>
            <span v-if="isAdmin" class="admin-tag">ADMIN</span>
          </span>
          <!-- Отображение баланса -->
          <span class="balance-display">💳 Balance: <strong>${{ userBalance.toFixed(2) }}</strong></span>
        </div>

        <div class="profile-actions">
          <!-- Форма пополнения баланса -->
          <form @submit.prevent="handleRecharge" class="recharge-form">
            <input v-model="rechargeAmount" type="number" min="10" step="10" class="recharge-info" required/>
            <button type="submit" :disabled="rechargeLoading" class="recharge-button">
              {{ rechargeLoading ? '...' : 'Recharge' }}
            </button>
          </form>
          <button @click="handleLogout" class="logout-button">Logout</button>
        </div>
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
      <h2>⚙️Admin Control Panel (Catalog & Inventory)</h2>
      <div class="admin-sections">

        <!-- Секция 1: Пополнение остатков существующего товара -->
        <div class="admin-section">
          <h3>📦 Add existing stock</h3>
          <div class="admin-grid-form">
            <div class="form-group">
              <label>Select Product:</label>
              <select v-model="adminSkuInput" class="admin-select-full">
                <option v-for="product in products"
                        :key="product.id"
                        :value="product.skuCode">{{ product.name }} ({{ product.skuCode }})
                </option>
              </select>
            </div>

            <div class="form-group">
              <label>Quantity to Add:</label>
              <input v-model="adminQtyInput" type="number" min="1" class="admin-input-full"/>
            </div>

            <div class="form-group action-group">
              <button @click="addStockToInventory" :disabled="adminLoading" class="admin-submit-button-full">
                {{ adminLoading ? 'Updating...' : 'Add Stock' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Секция 2: Создание нового товара в БД -->
        <div class="admin-section">
          <h3>✨ Create New Product from Scratch</h3>
          <div class="admin-grid-form">
            <div class="form-group">
              <label>Product Name</label>
              <input v-model="newName" type="text" placeholder="e.g. iPad Pro" class="admin-input-full"/>
            </div>

            <div class="form-group">
              <label>SKU Code</label>
              <input v-model="newSku" type="text" placeholder="e.g. ipad_pro" class="admin-input-full"/>
            </div>

            <div class="form-group">
              <label>Price ($)</label>
              <input v-model="newPrice" type="number" min="1" class="admin-input-full"/>
            </div>

            <div class="form-group">
              <label>Initial Oty</label>
              <input v-model="newQty" type="number" min="0" class="admin-input-full"/>
            </div>

            <div class="form-group">
              <label>Emoji</label>
              <input v-model="newEmoji" type="text" class="admin-input-full" style="text-align: center;"/>
            </div>

            <div class="form-group action-group">
              <button @click="createNewProduct" :disabled="adminLoading" class="admin-submit-button-full">
                Create Product
              </button>
            </div>
          </div>
        </div>

        <!-- Секция 3: Редактирование параметров существующего товара -->
        <div class="admin-section">
          <h3>✏️ Edit Product Details</h3>
          <div class="admin-grid-form">
            <div class="form-group">
              <label>Select Product to Edit:</label>
              <select v-model="editProductId" @change="handleEditProductChange" class="admin-select-full">
                <option value="" disabled>-- Chose Product --</option>
                <option
                    v-for="product in products"
                    :key="product.id"
                    :value="product.id"
                >{{ product.name }}
                </option>
              </select>
            </div>

            <template v-if="editProductId">
              <div class="form-group">
                <label>Name</label>
                <input v-model="editName" type="text" class="admin-input-full"/>
              </div>

              <div class="form-group">
                <label>SKU</label>
                <input v-model="editSku" type="text" class="admin-input-full"/>
              </div>

              <div class="form-group">
                <label>Price ($)</label>
                <input v-model="editPrice" type="number" min="1" class="admin-input-full"/>
              </div>

              <div class="form-group">
                <label>Quantity</label>
                <input v-model="editQty" type="number" min="0" class="admin-input-full"/>
              </div>

              <div class="form-group">
                <label>Emoji</label>
                <input v-model="editEmoji" type="text" class="admin-input-full" style="text-align: center;"/>
              </div>

              <div class="form-group action-group">
                <button @click="updateProductDetails" :disabled="adminLoading" class="admin-submit-button-full">
                  Save Changes
                </button>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Корзина товаров (показывается только после авторизации) -->
    <div v-if="currentUser" class="cart-panel">
      <h2>🛒 Shopping Cart (Redis Cache) <span v-if="getCartCount() > 0"
                                              class="cart-count-badge">{{ getCartCount() }}</span></h2>
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

            <td class="cart-qty-cell">
              <button
                  @click='removeOneFromCart(item.skuCode)'
                  class="cart-minus-button"
                  title="Return 1 item to shelf"
              >➖
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

          <button
              v-if="isAdmin"
              @click="deleteProduct(product.id)"
              class="delete-product-button"
              title="Delete product from database">
            🗑️
          </button>

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

<script src="./App.js"></script>
<style scoped src="./App.css"></style>