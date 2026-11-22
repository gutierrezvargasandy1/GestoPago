-- ===========================================================================
-- V4: Doble factor (TOTP) y sesion unica por cliente.
-- ===========================================================================

-- Segundo factor: codigo temporal de 6 digitos (RFC 6238, Google/Microsoft Authenticator).
ALTER TABLE cliente
    ADD COLUMN totp_secreto         VARCHAR(64),
    ADD COLUMN totp_activo          BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Ultimo intervalo de 30 s aceptado: un codigo ya usado no se acepta dos veces.
    ADD COLUMN totp_ultimo_paso     BIGINT,
    ADD COLUMN totp_intentos        SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN totp_bloqueado_hasta TIMESTAMPTZ;

-- Sesiones administradas por el servidor. El JWT lleva el sid; en cada peticion
-- se comprueba que esa sesion siga abierta, asi cerrar sesion o abrir otra en
-- otro dispositivo invalida el token anterior al instante.
CREATE TABLE sesion (
    id               BIGSERIAL    PRIMARY KEY,
    sid              VARCHAR(64)  NOT NULL,
    cliente_id       BIGINT       NOT NULL,
    plataforma       VARCHAR(10)  NOT NULL,
    creada_en        TIMESTAMPTZ  NOT NULL,
    ultima_actividad TIMESTAMPTZ  NOT NULL,
    expira_en        TIMESTAMPTZ  NOT NULL,
    cerrada_en       TIMESTAMPTZ,
    motivo_cierre    VARCHAR(30),
    CONSTRAINT fk_sesion_cliente     FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT uk_sesion_sid         UNIQUE (sid),
    CONSTRAINT chk_sesion_plataforma CHECK (plataforma IN ('WEB', 'MOVIL')),
    CONSTRAINT chk_sesion_motivo     CHECK (motivo_cierre IS NULL
        OR motivo_cierre IN ('CIERRE_USUARIO', 'REEMPLAZADA', 'EXPIRADA'))
);

-- A lo mas UNA sesion abierta por cliente. Lo garantiza la base de datos,
-- aunque dos inicios de sesion (web y movil) lleguen al mismo tiempo.
CREATE UNIQUE INDEX uk_sesion_abierta ON sesion (cliente_id) WHERE cerrada_en IS NULL;

ALTER TABLE sesion ENABLE ROW LEVEL SECURITY;
