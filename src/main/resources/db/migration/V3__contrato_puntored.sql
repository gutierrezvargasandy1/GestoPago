-- ===========================================================================
-- V3: Contrato real de PuntoRed (GestoPago) y endurecimiento para Supabase.
--
-- V1 y V2 no se modifican: Flyway guarda su checksum y cambiarlas romperia
-- cualquier base que ya las aplico. Todo cambio de esquema va en una version nueva.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. Catalogo.
--
-- PuntoRed identifica servicios y productos con enteros (getProductList). Los
-- codigos propios (id_servicio, id_producto) siguen siendo la llave de la API;
-- las columnas *_proveedor son las que viajan en sendTx. Asi la API no queda
-- atada a la numeracion del proveedor (capa anticorrupcion).
--
-- origen distingue el catalogo de ejemplo (V2, solo para simulador y pruebas)
-- del sincronizado con el proveedor. La aplicacion oculta el de ejemplo salvo
-- que pagos.catalogo.permitir-ejemplo=true.
-- ---------------------------------------------------------------------------
ALTER TABLE servicio
    ADD COLUMN id_servicio_proveedor INTEGER,
    ADD COLUMN origen                VARCHAR(20) NOT NULL DEFAULT 'SEMILLA',
    ADD COLUMN sincronizado_en       TIMESTAMPTZ;

ALTER TABLE servicio
    ADD CONSTRAINT chk_servicio_origen CHECK (origen IN ('SEMILLA', 'PROVEEDOR')),
    ADD CONSTRAINT chk_servicio_id_proveedor CHECK (origen <> 'PROVEEDOR' OR id_servicio_proveedor IS NOT NULL);

-- Un servicio del proveedor existe una sola vez, aunque dos sincronizaciones corran a la vez.
CREATE UNIQUE INDEX uk_servicio_proveedor ON servicio (id_servicio_proveedor) WHERE origen = 'PROVEEDOR';

ALTER TABLE producto
    ADD COLUMN id_producto_proveedor INTEGER,
    -- RECARGA: tiempo aire o PIN, la referencia es el telefono y el precio es fijo.
    -- PAGO_SERVICIO: se envia la referencia del recibo y el monto (montoPago).
    ADD COLUMN modalidad             VARCHAR(20) NOT NULL DEFAULT 'RECARGA',
    ADD COLUMN origen                VARCHAR(20) NOT NULL DEFAULT 'SEMILLA',
    ADD COLUMN leyenda               VARCHAR(500),
    ADD COLUMN sincronizado_en       TIMESTAMPTZ;

UPDATE producto SET modalidad = 'PAGO_SERVICIO' WHERE tipo_monto = 'ABIERTO';

ALTER TABLE producto
    ADD CONSTRAINT chk_producto_modalidad CHECK (modalidad IN ('RECARGA', 'PAGO_SERVICIO')),
    ADD CONSTRAINT chk_producto_origen CHECK (origen IN ('SEMILLA', 'PROVEEDOR')),
    ADD CONSTRAINT chk_producto_id_proveedor CHECK (origen <> 'PROVEEDOR' OR id_producto_proveedor IS NOT NULL),
    -- En una recarga el proveedor no recibe monto: el precio lo fija el producto.
    ADD CONSTRAINT chk_producto_recarga_fija CHECK (modalidad <> 'RECARGA' OR tipo_monto = 'FIJO');

-- Identificadores para el catalogo de ejemplo. Solo los reconoce el simulador
-- local; contra PuntoRed se usa el catalogo sincronizado.
UPDATE servicio s SET id_servicio_proveedor = v.id
FROM (VALUES ('TAE_TELCEL', 1001), ('TAE_MOVISTAR', 1002), ('CFE', 1003), ('SKY', 1004)) AS v(codigo, id)
WHERE s.id_servicio = v.codigo;

UPDATE producto p SET id_producto_proveedor = v.id
FROM (VALUES ('TEL_020', 2001), ('TEL_050', 2002), ('TEL_100', 2003), ('TEL_200', 2004), ('TEL_500', 2005),
             ('MOV_030', 2101), ('MOV_100', 2102), ('CFE_PAGO', 3001), ('SKY_RECARGA', 4001)) AS v(codigo, id)
WHERE p.id_producto = v.codigo;

-- ---------------------------------------------------------------------------
-- 2. Datos de canje. Las tarjetas de regalo devuelven un PIN y una leyenda que
-- el cliente necesita para usar lo que compro (PuntoRed exige entregarlos).
-- ---------------------------------------------------------------------------
ALTER TABLE transaccion_pago
    ADD COLUMN pin_canje           VARCHAR(100),
    ADD COLUMN instrucciones_canje VARCHAR(1000);

-- ---------------------------------------------------------------------------
-- 3. Bitacora de intercambios con el proveedor.
--
-- PuntoRed pide conservar el registro de todas las peticiones y respuestas. Se
-- guarda lo necesario para aclaraciones sin guardar secretos: nunca el token,
-- la API key, el campo "signed" ni el PIN; telefono y referencia van
-- enmascarados (la referencia completa ya vive en transaccion_pago).
-- ---------------------------------------------------------------------------
CREATE TABLE bitacora_proveedor (
    id                  BIGSERIAL    PRIMARY KEY,
    operacion           VARCHAR(20)  NOT NULL,
    referencia_externa  VARCHAR(40),
    intento             SMALLINT     NOT NULL DEFAULT 1,
    solicitud           TEXT,
    status_http         INTEGER,
    codigo              VARCHAR(20),
    respuesta           TEXT,
    error               VARCHAR(80),
    duracion_ms         INTEGER      NOT NULL,
    trace_id            VARCHAR(64),
    creado_en           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_bitacora_operacion CHECK (operacion IN ('AUTENTICACION', 'ENVIO', 'CONSULTA', 'CATALOGO')),
    CONSTRAINT chk_bitacora_duracion  CHECK (duracion_ms >= 0)
);

CREATE INDEX idx_bitacora_referencia ON bitacora_proveedor (referencia_externa);
CREATE INDEX idx_bitacora_fecha ON bitacora_proveedor (creado_en);

-- ---------------------------------------------------------------------------
-- 4. Sincronizaciones del catalogo. PuntoRed limita getProductList a 3 llamadas
-- por dia; el registro vive en la base para que el limite se respete aunque la
-- aplicacion se reinicie.
-- ---------------------------------------------------------------------------
CREATE TABLE sincronizacion_catalogo (
    id            BIGSERIAL   PRIMARY KEY,
    iniciado_en   TIMESTAMPTZ NOT NULL,
    terminado_en  TIMESTAMPTZ,
    exitosa       BOOLEAN,
    productos     INTEGER,
    detalle       VARCHAR(80)
);

CREATE INDEX idx_sincronizacion_inicio ON sincronizacion_catalogo (iniciado_en);

-- ---------------------------------------------------------------------------
-- 5. Row Level Security.
--
-- En Supabase, las tablas del esquema public quedan expuestas por la API REST
-- (PostgREST) a los roles anon y authenticated. Con RLS activo y sin politicas,
-- esos roles no ven ni modifican ninguna fila. La aplicacion se conecta como
-- duena de las tablas, y a la duena RLS no le aplica: su acceso no cambia.
-- En un PostgreSQL normal estas sentencias no tienen otro efecto.
-- ---------------------------------------------------------------------------
ALTER TABLE cliente                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE cuenta                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE servicio                ENABLE ROW LEVEL SECURITY;
ALTER TABLE producto                ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaccion_pago        ENABLE ROW LEVEL SECURITY;
ALTER TABLE movimiento_cuenta       ENABLE ROW LEVEL SECURITY;
ALTER TABLE bitacora_proveedor      ENABLE ROW LEVEL SECURITY;
ALTER TABLE sincronizacion_catalogo ENABLE ROW LEVEL SECURITY;
