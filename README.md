# Pagos de servicios con PuntoRed (GestoPago)

API REST en **Spring Boot 4.1 / Java 21** con la que los clientes de un banco pagan servicios (tiempo aire, luz, televisión) con cargo a su cuenta. La operación se envía a **PuntoRed / GestoPago** (`POST /sistema/service/sendTx.do`) con su contrato real: JSON cifrado con **AES-256-CBC** en un formulario (`random` + `signed`), token Bearer de 24 h, `X-API-Key` y respuestas **XML**.

El objetivo no es solo consumir el endpoint. La API garantiza que **nunca se cobre dos veces, que no haya sobregiros con peticiones simultáneas y que un timeout no se trate como fallo**.

> El análisis de riesgos, la matriz de escenarios de fallo y la justificación de cada decisión están en **[docs/DISENO.md](docs/DISENO.md)**.

## Qué incluye

| Requisito | Cómo se cumple |
|---|---|
| Validar servicio, producto y monto | Catálogo sincronizado a diario con `getProductList` (máx. 3 llamadas por día): monto fijo o rango, formato de referencia. El monto del cliente nunca se acepta sin contrastarlo. |
| Identidad y autorización | JWT propio (HS256), scope `pagos`, la cuenta debe pertenecer al cliente y el permiso se vuelve a revisar en base de datos. |
| Cuenta activa, saldo y límite diario | Se validan **con la cuenta bloqueada** (`SELECT … FOR UPDATE`). |
| Sin cargos duplicados | `Idempotency-Key` + huella SHA-256 + `UNIQUE` en base de datos; UPC único por operación ante el proveedor. |
| Concurrencia | Bloqueo pesimista por cuenta, `@Version` como segunda red y `CHECK (saldo >= 0)` como última. |
| Timeouts y errores del proveedor | Códigos `01`/`02`/`06`/`82` y fallos de red clasificados en *no procesada* (se devuelve el dinero) o *indeterminada* (se retiene y se concilia). `sendTx` nunca se reintenta a ciegas. |
| Conciliación | Job que pregunta con `confirmTx` (mismo UPC y hora), con espera exponencial, doble confirmación antes de devolver, plazo de 24 h y revisión manual. |
| Token Bearer seguro | Solo variables de entorno o `.env` ignorado por git; se obtiene y renueva solo con las credenciales de distribuidor; nunca en logs ni respuestas. |
| Bitácora del proveedor | Cada petición y respuesta queda en `bitacora_proveedor`, sin token, llave, PIN ni referencia completa. |
| Errores centralizados | `@RestControllerAdvice` + `ProblemDetail` (RFC 9457) con código estable y `traceId`. |
| Pruebas | Unitarias (cifrado verificado contra `openssl`, XML a prueba de XXE, adaptador con WireMock) e integración contra PostgreSQL real. |

## Requisitos

- **Java 21.** El proyecto trae Maven Wrapper (`mvnw`), así que no necesitas instalar Maven.
- Una base **PostgreSQL**: Supabase o la de `docker compose`.
- **Docker** para el simulador local y para las pruebas de integración (sin Docker, ver [Pruebas sin Docker](#pruebas-sin-docker)).

## Configuración (`.env`)

Todas las variables se leen del entorno o de un archivo `.env` en la raíz del proyecto (ver `.env.example`). **El `.env` está en `.gitignore`: nunca lo subas al repositorio.** Si falta algo obligatorio, la aplicación no arranca y el mensaje dice qué variable falta.

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Sí | Conexión a PostgreSQL |
| `DB_ESQUEMA` | No | Esquema de las tablas (por defecto `public`; en Supabase usa `pagos`) |
| `DB_POOL_MAXIMO` | No | Conexiones del pool (por defecto 10; en Supabase, 5) |
| `JWT_SECRETO` | Sí | Clave HS256 para firmar los JWT de la API (≥ 32 bytes) |
| `GESTOPAGO_ID_DISTRIBUIDOR`, `GESTOPAGO_CODIGO_DISPOSITIVO`, `GESTOPAGO_PASSWORD` | Sí* | Credenciales de distribuidor: la API obtiene el token y lo renueva sola |
| `GESTOPAGO_AES_KEY`, `GESTOPAGO_AES_IV` | Sí | Llave AES-256 (base64, 32 bytes) e IV (base64, 16 bytes) del proveedor |
| `GESTOPAGO_API_KEY` | No | Se envía en `X-API-Key` si el proveedor la asignó |
| `GESTOPAGO_TOKEN` | No* | Token fijo en lugar de las credenciales (no se renueva solo) |
| `GESTOPAGO_URL` | No | Por defecto `https://gestopago.portalventas.net` |
| `GESTOPAGO_UNIDAD` | No | Campo `unidad` de sendTx, 3 a 25 caracteres (por defecto `API-PAGOS`) |
| `GESTOPAGO_ENDPOINT_CONSULTA` | No | Ruta de `confirmTx`. Vacía: las operaciones dudosas van a revisión manual |
| `GESTOPAGO_CODIGOS_NO_APLICADA` | No | Código(s) con que `confirmTx` responde "no aplicada" (separados por coma) |
| `GESTOPAGO_CREDENCIALES_EN_URL` | No | `true` para mandar las credenciales en la URL, como el ejemplo de la documentación |

\* Las credenciales de distribuidor o el token fijo: uno de los dos.

### Con Supabase

En Supabase usa el **pooler en modo sesión (puerto 5432)**, no el de modo transacción (6543): este último pierde la configuración de la sesión y Flyway no puede migrar. Y usa un esquema propio, para que las tablas no queden expuestas por la API REST de Supabase:

```properties
DB_URL=jdbc:postgresql://aws-1-us-west-2.pooler.supabase.com:5432/postgres?sslmode=require
DB_USERNAME=postgres.<id-del-proyecto>
DB_ESQUEMA=pagos
DB_POOL_MAXIMO=5
```

Flyway crea el esquema y las tablas al arrancar. Como segunda barrera, todas las tablas tienen Row Level Security activo. **No apuntes las pruebas a Supabase**: vacían tablas.

## Arrancar contra PuntoRed

```bash
# Clientes y cuentas de ejemplo + proveedor real
./mvnw spring-boot:run -Dspring-boot.run.profiles=datos-demo
#    Windows: mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=datos-demo
```

Al arrancar, la API sincroniza el catálogo de PuntoRed (una vez al día). Los servicios y productos reales se consultan con:

```bash
GET /api/v1/catalogo/servicios
GET /api/v1/catalogo/servicios/{idServicio}/productos
```

Los códigos de servicio y producto son los números del proveedor (por ejemplo `"idServicio":"17"`). El catálogo de ejemplo (`TAE_TELCEL`, `CFE`…) solo existe con el simulador.

**Usuarios de demostración** (perfil `datos-demo`, contraseña `Demo-2026!`):

| Usuario | Cuenta | Saldo | Particularidad |
|---|---|---|---|
| `ana` | `1000000001` | 1,500.00 | Cuenta activa |
| `ana` | `1000000002` | 300.00 | Cuenta **bloqueada** |
| `bruno` | `2000000001` | 80.00 | Saldo bajo |
| `carla` | `3000000001` | 1,000.00 | **Sin permiso** de pagos |

## Demo con el simulador (sin proveedor real)

`simulador/SimuladorPuntoRed.java` implementa el contrato de PuntoRed (autenticación, cifrado, XML, `confirmTx`, `getProductList`) y solo usa el JDK. Ninguna operación sale a internet.

```bash
docker compose up -d                 # PostgreSQL local + simulador
#   (con Supabase: docker compose up -d simulador)
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

El perfil `demo` = `datos-demo` + `simulador`. Importa en Postman `postman/pagos-servicios.postman_collection.json` y ejecuta las peticiones en orden.

**El simulador decide por la terminación del teléfono o la referencia:**

| Termina en | Respuesta del simulador | Resultado en la API |
|---|---|---|
| `0000` | `02` saldo insuficiente | 422, el monto se devuelve |
| `0006` | `06` duplicada | 409 `PAGO_DUPLICADO`, el monto se devuelve |
| `8282` | `82` sin respuesta del carrier | 202 `EN_CONCILIACION`; confirmTx "no aplicada" ⇒ se devuelve |
| `9999` | Responde a los 8 s (la API espera 5) | 202 `EN_CONCILIACION`; confirmTx "aplicada" ⇒ `CONFIRMADA` |
| `5000` | HTTP 500 | 202 `EN_CONCILIACION`; confirmTx "no aplicada" ⇒ se devuelve |
| `7777` | `01` con PIN y leyenda | 201 con `pin` e `instrucciones` |
| otra | `01` | 201 `CONFIRMADA` |

### Con curl

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"ana","password":"Demo-2026!"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -i -X POST localhost:8080/api/v1/pagos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"numeroCuenta":"1000000001","idServicio":"TAE_TELCEL","idProducto":"TEL_100","referencia":"5512345678","monto":100.00}'
```

## Endpoints

| Método | Ruta | Autenticación | Descripción |
|---|---|---|---|
| `POST` | `/api/v1/auth/token` | — | Login. Devuelve un JWT de 15 min |
| `GET` | `/api/v1/catalogo/servicios` | JWT | Servicios disponibles |
| `GET` | `/api/v1/catalogo/servicios/{idServicio}/productos` | JWT | Productos, modalidad y reglas de monto |
| `POST` | `/api/v1/pagos` | JWT + scope `pagos` + `Idempotency-Key` | Pagar un servicio |
| `GET` | `/api/v1/pagos/{id}` | JWT + scope `pagos` | Estado de un pago propio (incluye el PIN de una tarjeta de regalo) |
| `GET` | `/actuator/health` | — | Salud (incluye el circuit breaker) |

### Respuestas de `POST /api/v1/pagos`

| HTTP | Cuándo | ¿Se cobró? |
|---|---|---|
| **201** | El proveedor confirmó (`01`) | Sí |
| **202** | Resultado pendiente (`EN_CONCILIACION` / `REVISION_MANUAL`) | Retenido hasta conciliar |
| **200** | Clave repetida: se devuelve la operación original (`Idempotent-Replayed: true`) | No se vuelve a cobrar |
| 400 | Formato inválido, campo no permitido, falta `Idempotency-Key` | No |
| 401 / 403 | Sin token válido / sin permiso / cuenta ajena | No |
| 409 | Clave reutilizada con otros datos, operación en curso, cuenta ocupada o **duplicada en el proveedor** (`06`) | No (o devuelto) |
| 422 | Regla de negocio o rechazo del proveedor (`02`) | No (o devuelto) |
| 503 | El proveedor no procesó la operación | No (o devuelto) |

Todos los errores usan `application/problem+json` con `codigo`, `traceId` y, si aplica, `transaccionId`.

## Pruebas

```bash
./mvnw test
```

- **Unitarias**: no requieren nada externo. Cubren dominio, servicios con dobles de prueba, el cifrado (contra un vector calculado con `openssl`), la lectura segura del XML y el adaptador contra WireMock.
- **Integración** (`*IntegracionTest`): aplicación completa contra **PostgreSQL real** (Testcontainers) y PuntoRed simulado con su contrato real. Si no hay Docker se **omiten** en lugar de fallar. Usan sus propias credenciales de prueba: nunca las del `.env`.

### Pruebas sin Docker

Crea una base **vacía y exclusiva para pruebas** (las pruebas la vacían) e indícala:

```bash
export TEST_DB_URL=jdbc:postgresql://localhost:5432/pagos_test
export TEST_DB_USERNAME=postgres
export TEST_DB_PASSWORD=tu-contrasena
./mvnw test
```

## Estructura

```
src/main/java/com/utng/pagosservicios
├── web/            Controllers, DTOs, filtro de trazabilidad y @RestControllerAdvice
├── seguridad/      Spring Security, emisión de JWT, delegación de errores 401/403
├── servicio/       Pago, fondos, conciliación, sincronización del catálogo y excepciones
├── dominio/        Entidades con sus reglas (cuenta, catálogo, transacción, UPC)
├── repositorio/    Spring Data JPA y JDBC (bloqueos, conciliación, bitácora)
├── proveedor/      Puertos y adaptador de PuntoRed (cifrado AES, XML, RestClient, circuit breaker)
├── config/         Propiedades, reloj, tareas programadas y validaciones de arranque
└── util/           Enmascarado y descripción de errores para logs
src/main/resources/db/migration   Esquema (Flyway) con las garantías de integridad
src/main/resources/db/demo        Clientes de ejemplo (solo perfil datos-demo)
simulador/                        Simulador de PuntoRed para la demo
```
