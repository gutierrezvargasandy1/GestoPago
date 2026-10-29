# --- etapa 1: compilacion ---
from maven:3.9.9-eclipse-temurin-21-alpine as builder
workdir /app

# copiar pom.xml y descargar dependencias para aprovechar la caché
copy pom.xml .
run mvn dependency:go-offline -b

# copiar el codigo fuente y compilar
copy src ./src
run mvn clean package -DskipTests

# --- etapa 2: ejecucion ---
from eclipse-temurin:21-jre-alpine
workdir /app

# usuario sin privilegios por seguridad
run addgroup -S appgroup && adduser -S -G appgroup appuser

# copiar el JAR generado
copy --from=builder /app/target/*.jar app.jar

run chown -R appuser:appgroup /app
user appuser

# exponer puerto predeterminado
expose 8080

# opciones de memoria para contenedores java
env JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

entrypoint ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]