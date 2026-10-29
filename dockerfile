# --- Etapa 1: Compilación ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copiar archivos de configuración de dependencias primero para aprovechar la caché
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente y compilar omitiendo tests
COPY src ./src
RUN mvn clean package -DskipTests

# --- Etapa 2: Ejecución ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear un usuario sin privilegios por seguridad
RUN addgroup -S appgroup && adduser -S -G appgroup appuser

# Copiar el JAR generado apuntando directamente al directorio target
COPY --from=builder /app/target/*.jar app.jar

# Cambiar permisos del directorio de la aplicación al usuario creado
RUN chown -R appuser:appgroup /app
USER appuser

# Render asigna dinámicamente el puerto mediante la variable PORT
EXPOSE 8080

# Configuraciones recomendadas para contenedores Java
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]