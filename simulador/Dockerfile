# Simulador de PuntoRed (GestoPago) para demostraciones: NO es el proveedor real.
# Se publica en Render como un Web Service aparte (raiz: simulador/).
# Render indica en PORT el puerto que debe escuchar; en local se usa 8089.
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /simulador
COPY SimuladorPuntoRed.java .
EXPOSE 8089
CMD ["sh", "-c", "SIMULADOR_PUERTO=${PORT:-8089} exec java SimuladorPuntoRed.java"]
