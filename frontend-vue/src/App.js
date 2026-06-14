import {ref, onMounted} from 'vue'
import axios from "axios";

const API_BASE_URL = 'http://localhost:8080/api'

export default {
    setup() {
        // Данные пользователя и авторизация
        const currentUser = ref(localStorage.getItem('username') || null)
        const token = ref(localStorage.getItem('token') || null)
        const isAdmin = ref(false)

        // Финансовый баланс пользователя
        const userBalance = ref(0)
        const rechargeAmount = ref(100) // По умолчанию предлагаем пополнять на 100 баксов
        const rechargeLoading = ref(false)

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
        const adminSkuInput = ref('')
        const adminQtyInput = ref(10)
        const adminLoading = ref(false)

        // Состояние для создания нового товара
        const newSku = ref('')
        const newName = ref('')
        const newPrice = ref(100)
        const newQty = ref(10)
        const newEmoji = ref('📦')

        // Список товаров (загружается с БД)
        const products = ref([])

        // Состояние для редактирования существующего товара
        const editProductId = ref('')
        const editName = ref('')
        const editSku = ref('')
        const editPrice = ref(0)
        const editQty = ref(0)
        const editEmoji = ref('')

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

        // Загрузка каталога товаров из бд
        const fetchProductsFromDb = async () => {
            try {
                const response = await axios.get(`${API_BASE_URL}/inventory/products`)
                products.value = response.data
                    .sort((a, b) => b.price - a.price)
                    .map(p => ({
                    ...p,
                    inStock: p.isInStock,
                    loading: false
                }))

                // По умолчанию выставляем первый товар в селекторе пополнения склада
                if (products.value.length > 0 && !adminSkuInput.value) {
                    adminSkuInput.value = products.value[0].skuCode
                }
            } catch (error) {
                console.error('Failed to fetch products:', error)
                showToast('Failed to load product catalog from DB', 'error')
            }
        }

        // Запрос баланса пользователя из user-service
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

        // Запрос на пополнение баланса в user-service
        const handleRecharge = async () => {
            if (!token.value || rechargeAmount.value <= 0) return
            rechargeLoading.value = true
            try {
                const headers = {Authorization: `Bearer ${token.value}`}
                const response = await axios.post(`${API_BASE_URL}/user/recharge`, null, {
                    params: {amount: rechargeAmount.value},
                    headers
                })

                // Бэк возвращает обновленный баланс
                userBalance.value = response.data
                showToast(`Successfully recharged $${rechargeAmount.value}!`, 'success')
            } catch (error) {
                showToast('Failed to recharge balance', 'error')
            } finally {
                rechargeLoading.value = false
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

                // Загружаем баланс
                await fetchBalance()

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
            userBalance.value = 0
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

                // Обновляем остатки на витрине
                await fetchProductsFromDb()
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
                await fetchProductsFromDb()
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

                await fetchProductsFromDb()
                await fetchBalance()
            } catch (error) {
                console.error('Checkout failed:', error)
                const errorMsg = error.response?.data || 'Checkout failed'
                showToast(errorMsg, 'error')
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
                await fetchProductsFromDb()
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

        // Создание нового продукта в бд для админа
        const createNewProduct = async () => {
            if (!token.value || !isAdmin.value) return
            if (!newSku.value || !newName.value || newPrice.value <= 0) {
                showToast('Please fill in all product fields correctly', 'error')
                return
            }
            adminLoading.value = true
            try {
                const headers = {Authorization: `Bearer ${token.value}`}
                const payload = {
                    skuCode: newSku.value,
                    name: newName.value,
                    price: parseFloat(newPrice.value),
                    quantity: parseInt(newQty.value),
                    emoji: newEmoji.value
                }

                await axios.post(`${API_BASE_URL}/inventory/products`, payload, {headers})
                showToast(`Product ${newName.value} created successfully in DB!`, 'success')

                // Очищаем форму
                newSku.value = ''
                newName.value = ''
                newPrice.value = 100
                newQty.value = 10
                newEmoji.value = '📦'

                await fetchProductsFromDb()
            } catch (error) {
                console.error('Failed to create product:', error)
                showToast('Failed to create product', 'error')
            } finally {
                adminLoading.value = false
            }
        }

        // Удаление продукта из бд для админа
        const deleteProduct = async (productId) => {
            if (!token.value || !isAdmin.value) return
            if (!confirm('Are you sure you want to delete this product from database?')) return
            try {
                const headers = {Authorization: `Bearer ${token.value}`}
                await axios.delete(`${API_BASE_URL}/inventory/products/${productId}`, {headers})
                showToast('Product successfully deleted!', 'success')
                await fetchProductsFromDb()
            } catch (error) {
                console.error('Failed to delete product:', error)
                showToast('Failed to delete product', 'error')
            }
        }

        // Метод автозаполнения формы редактирования
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

        // Сохранение измененных параметров товара в БД
        const updateProductDetails = async () => {
            if (!token.value || !isAdmin.value || !editProductId.value) return
            adminLoading.value = true
            try {
                const headers = {Authorization: `Bearer ${token.value}`}
                const payload = {
                    skuCode: editSku.value,
                    name: editName.value,
                    price: parseFloat(editPrice.value),
                    quantity: parseInt(editQty.value),
                    emoji: editEmoji.value
                }

                await axios.put(`${API_BASE_URL}/inventory/products/${editProductId.value}`, payload, {headers})
                showToast(`Product ${editName.value} updates successfully!`, 'success')

                editProductId.value = ''

                await fetchProductsFromDb()
            } catch (error) {
                console.error('Failed to update product details:', error)
                showToast('Failed to update product', 'error')
            } finally {
                adminLoading.value = false
            }
        }

        // Подсчет общей стоимости корзины
        const getCartTotal = () => {
            return cart.value.items.reduce((total, item) => total + (item.price * item.quantity), 0)
        }

        const getCartCount = () => {
            return cart.value.items.reduce((total, item) => total + item.quantity, 0)
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
            fetchProductsFromDb()
            if (token.value) {
                checkUserRoles()
                fetchBalance()
                fetchCart()
            }
        })
        return {
            currentUser, token, isAdmin,
            userBalance, rechargeAmount, rechargeLoading,
            cart, cartLoading,
            authMode, usernameInput, passwordInput, emailInput, roleInput, authLoading,
            adminSkuInput, adminQtyInput, adminLoading,
            newSku, newName, newPrice, newQty, newEmoji,
            editProductId, editName, editSku, editPrice, editQty, editEmoji,
            products, toast,
            handleRegister, handleLogin, handleLogout,
            addToCart, clearCart, checkoutCart,
            addStockToInventory, createNewProduct, deleteProduct,
            handleEditProductChange, updateProductDetails,
            getCartTotal, getCartCount, handleRecharge
        }
    }
}