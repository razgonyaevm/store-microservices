FROM eclipse-temurin:21-jre-alpine

# Рабочая директория
WORKDIR /app

# Копируем скомпилированный jar внутрь контейнера
COPY target/*.jar app.jar

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]