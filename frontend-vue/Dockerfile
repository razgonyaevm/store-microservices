# Сборка статических файлов Vue.js
FROM node:20-alpine AS build-stage

WORKDIR /app

# Копируем файлы описания зависимостей
COPY package*.json ./

# Устанавливаем зависимости
RUN npm install

# Копируем весь исходный код фронтенда
COPY . .

# Собираем проект для продакшена (создается папка dist)
RUN npm run build

# Запуск веб-сервера Nginx для раздачи статики
FROM nginx:1.25-alpine AS production-stage

# Копируем скомпилированные файлы из предыдущего этапа в папку Nginx по умолчанию
COPY --from=build-stage /app/dist /usr/share/nginx/html

# Открываем стандартный порт HTTP
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]