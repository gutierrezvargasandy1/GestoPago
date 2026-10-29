-- ===========================================================================
-- V1: Esquema inicial.
--
-- Las garantias de integridad del dinero viven aqui y no solo en el codigo:
-- el codigo se puede saltar (un bug, una carrera, un script manual); un
-- constraint de base de datos no.
-- ===========================================================================

CREATE TABLE cliente (
    id                 BIGSERIAL    PRIMARY KEY,
    usuario            VARCHAR(100) NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    nombre             VARCHAR(200) NOT NULL,
    activo             BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Autorizacion especifica para pagar servicios (un cliente puede estar
    -- activo y aun asi no tener permitido este tipo de operacion).
    pagos_habilitados  BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_cliente_usuario UNIQUE (usuario)
);

-- ---------------------------------------------------------------------------
-- chk_cuenta_saldo es la ultima linea de defensa contra el sobregiro: si por un
-- error de logica se intentara dejar la cuenta en negativo, la transaccion
-- falla en el commit en lugar de guardar un saldo invalido.
-- ---------------------------------------------------------------------------
CREATE TABLE cuenta (
    id             BIGSERIAL     PRIMARY KEY,
    cliente_id     BIGINT        NOT NULL,
    numero         VARCHAR(20)   NOT NULL,
    saldo          NUMERIC(15,2) NOT NULL DEFAULT 0,
    estado         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVA',
    limite_diario  NUMERIC(15,2) NOT NULL DEFAULT 10000.00,
    version        BIGINT        NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_cuenta_cliente   FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT uk_cuenta_numero    UNIQUE (numero),
    CONSTRAINT chk_cuenta_saldo    CHECK (saldo >= 0),
    CONSTRAINT chk_cuenta_limite   CHECK (limite_diario > 0),
    CONSTRAINT chk_cuenta_estado   CHECK (estado IN ('ACTIVA', 'BLOQUEADA', 'CANCELADA'))
);

CREATE INDEX idx_cuenta_cliente ON cuenta (cliente_id);

-- ---------------------------------------------------------------------------
-- Catalogo. El monto que manda el cliente es solo una propuesta: la autoridad
-- sobre que monto es valido son estas restricciones, del lado del servidor.
-- ---------------------------------------------------------------------------
CREATE TABLE servicio (
    id           BIGSERIAL    PRIMARY KEY,
    id_servicio  VARCHAR(50)  NOT NULL,
    nombre       VARCHAR(200) NOT NULL,
    categoria    VARCHAR(50),
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_servicio_id_servicio UNIQUE (id_servicio)
);

CREATE TABLE producto (
    id                   BIGSERIAL     PRIMARY KEY,
    servicio_id          BIGINT        NOT NULL,
    id_producto          VARCHAR(50)   NOT NULL,
    nombre               VARCHAR(200)  NOT NULL,
    tipo_monto           VARCHAR(20)   NOT NULL,
    monto_fijo           NUMERIC(15,2),
    monto_minimo         NUMERIC(15,2),
    monto_maximo         NUMERIC(15,2),
    multiplo             NUMERIC(15,2),
    longitud_referencia  INTEGER,
    patron_referencia    VARCHAR(100),
    comision             NUMERIC(15,2) NOT NULL DEFAULT 0,
    activo               BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_producto_servicio    FOREIGN KEY (servicio_id) REFERENCES servicio (id),
    CONSTRAINT uk_producto             UNIQUE (servicio_id, id_producto),
    CONSTRAINT chk_producto_tipo_monto CHECK (tipo_monto IN ('FIJO', 'ABIERTO')),
    -- Un producto mal capturado se rechaza al insertarlo, no al cobrar.
    CONSTRAINT chk_producto_fijo CHECK (tipo_monto <> 'FIJO' OR (monto_fijo IS NOT NULL AND monto_fijo > 0)),
    CONSTRAINT chk_producto_abierto CHECK (
        tipo_monto <> 'ABIERTO' OR (
            monto_minimo IS NOT NULL AND monto_maximo IS NOT NULL
            AND monto_minimo > 0 AND monto_minimo <= monto_maximo
        )
    ),
    CONSTRAINT chk_producto_multiplo CHECK (multiplo IS NULL OR multiplo > 0),
    CONSTRAINT chk_producto_comision CHECK (comision >= 0)
);

CREATE INDEX idx_producto_servicio ON producto (servicio_id);

-- ---------------------------------------------------------------------------
-- Transaccion de pago (ver maquina de estados en docs/DISENO.md):
--
--   DEBITADA -> ENVIADA -> CONFIRMADA
--                       -> REVERSADA          (rechazo o no procesada)
--                       -> EN_CONCILIACION -> CONFIRMADA | REVERSADA | REVISION_MANUAL
--   DEBITADA -> REVERSADA                     (nunca se envio)
--
-- uk_transaccion_idempotencia impide el cargo duplicado: con dos peticiones
-- simultaneas con la misma clave, una inserta y la otra choca con el UNIQUE.
-- Lo decide la base de datos, sin ventana de carrera.
--
-- referencia_externa es el identificador que viaja al proveedor y la llave
-- para preguntarle despues que paso con la operacion.
-- ---------------------------------------------------------------------------
CREATE TABLE transaccion_pago (
    id                      BIGSERIAL     PRIMARY KEY,
    cliente_id              BIGINT        NOT NULL,
    cuenta_id               BIGINT        NOT NULL,
    producto_id             BIGINT        NOT NULL,
    clave_idempotencia      VARCHAR(80)   NOT NULL,
    -- SHA-256 de los parametros: detecta una clave reutilizada con otros datos.
    huella_solicitud        VARCHAR(64)   NOT NULL,
    referencia_externa      VARCHAR(40)   NOT NULL,
    referencia_servicio     VARCHAR(40)   NOT NULL,
    monto                   NUMERIC(15,2) NOT NULL,
    comision                NUMERIC(15,2) NOT NULL,
    monto_total             NUMERIC(15,2) NOT NULL,
    estado                  VARCHAR(30)   NOT NULL,
    motivo                  VARCHAR(40),
    -- Ultimo codigo del proveedor o clasificacion del fallo (HTTP_500, TIMEOUT_LECTURA...).
    codigo_resultado        VARCHAR(40),
    folio_proveedor         VARCHAR(80),
    autorizacion_proveedor  VARCHAR(80),
    intentos_consulta       INTEGER       NOT NULL DEFAULT 0,
    proximo_intento_en      TIMESTAMPTZ,
    trace_id                VARCHAR(64),
    creado_en               TIMESTAMPTZ   NOT NULL,
    enviado_en              TIMESTAMPTZ,
    resuelto_en             TIMESTAMPTZ,
    actualizado_en          TIMESTAMPTZ   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_transaccion_cliente  FOREIGN KEY (cliente_id)  REFERENCES cliente (id),
    CONSTRAINT fk_transaccion_cuenta   FOREIGN KEY (cuenta_id)   REFERENCES cuenta (id),
    CONSTRAINT fk_transaccion_producto FOREIGN KEY (producto_id) REFERENCES producto (id),
    CONSTRAINT uk_transaccion_idempotencia       UNIQUE (cliente_id, clave_idempotencia),
    CONSTRAINT uk_transaccion_referencia_externa UNIQUE (referencia_externa),
    CONSTRAINT chk_transaccion_monto    CHECK (monto > 0),
    CONSTRAINT chk_transaccion_comision CHECK (comision >= 0),
    CONSTRAINT chk_transaccion_total    CHECK (monto_total = monto + comision),
    CONSTRAINT chk_transaccion_intentos CHECK (intentos_consulta >= 0),
    CONSTRAINT chk_transaccion_estado   CHECK (estado IN (
        'DEBITADA', 'ENVIADA', 'EN_CONCILIACION', 'REVISION_MANUAL', 'CONFIRMADA', 'REVERSADA'
    )),
    CONSTRAINT chk_transaccion_resuelta CHECK (
        estado NOT IN ('CONFIRMADA', 'REVERSADA') OR resuelto_en IS NOT NULL
    )
);

-- El job de conciliacion busca por estado y fecha del siguiente intento.
CREATE INDEX idx_transaccion_conciliacion ON transaccion_pago (estado, proximo_intento_en);
-- El limite diario suma las operaciones del dia por cuenta.
CREATE INDEX idx_transaccion_cuenta_fecha ON transaccion_pago (cuenta_id, creado_en);

-- ---------------------------------------------------------------------------
-- Movimientos de cuenta (ledger). Solo insercion: nunca se actualiza ni se
-- borra. El saldo de cuenta es un acumulado; la verdad auditable es la suma de
-- estos movimientos.
--
-- uk_movimiento_tx_tipo garantiza que una transaccion tenga a lo mas un CARGO y
-- un ABONO: ni cobro doble ni reverso doble, aunque dos procesos lo intenten.
-- ---------------------------------------------------------------------------
CREATE TABLE movimiento_cuenta (
    id                   BIGSERIAL     PRIMARY KEY,
    cuenta_id            BIGINT        NOT NULL,
    transaccion_pago_id  BIGINT,
    tipo                 VARCHAR(20)   NOT NULL,
    monto                NUMERIC(15,2) NOT NULL,
    saldo_resultante     NUMERIC(15,2) NOT NULL,
    descripcion          VARCHAR(200),
    creado_en            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_movimiento_cuenta      FOREIGN KEY (cuenta_id)           REFERENCES cuenta (id),
    CONSTRAINT fk_movimiento_transaccion FOREIGN KEY (transaccion_pago_id) REFERENCES transaccion_pago (id),
    CONSTRAINT uk_movimiento_tx_tipo     UNIQUE (transaccion_pago_id, tipo),
    CONSTRAINT chk_movimiento_tipo  CHECK (tipo IN ('CARGO', 'ABONO')),
    CONSTRAINT chk_movimiento_monto CHECK (monto > 0),
    CONSTRAINT chk_movimiento_saldo CHECK (saldo_resultante >= 0)
);

CREATE INDEX idx_movimiento_cuenta ON movimiento_cuenta (cuenta_id, creado_en);
