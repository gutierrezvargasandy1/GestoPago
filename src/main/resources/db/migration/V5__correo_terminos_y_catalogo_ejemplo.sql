-- V5: correo como usuario, aceptacion de terminos y catalogo de ejemplo mas completo.

-- El usuario de los clientes nuevos es su correo (las cuentas anteriores conservan
-- su usuario). Se guarda que version de los terminos acepto y cuando.
ALTER TABLE cliente
    ADD COLUMN terminos_version       VARCHAR(20),
    ADD COLUMN terminos_aceptados_en  TIMESTAMPTZ;

-- Descripcion de los productos de ejemplo (V2). Solo se ven con
-- pagos.catalogo.permitir-ejemplo=true (perfiles simulador y demo).
UPDATE producto SET leyenda = 'Recarga de tiempo aire Telcel. El saldo se abona al número de 10 dígitos en cuanto se confirma la compra.'
WHERE origen = 'SEMILLA' AND id_producto LIKE 'TEL\_%' AND leyenda IS NULL;
UPDATE producto SET leyenda = 'Recarga de tiempo aire Movistar para cualquier número de 10 dígitos.'
WHERE origen = 'SEMILLA' AND id_producto LIKE 'MOV\_%' AND leyenda IS NULL;
UPDATE producto SET leyenda = 'Pago de tu recibo de luz CFE. Usa el número de servicio de 12 dígitos impreso en el recibo.'
WHERE origen = 'SEMILLA' AND id_producto = 'CFE_PAGO' AND leyenda IS NULL;
UPDATE producto SET leyenda = 'Recarga o pago de Sky con el número de cuenta de 11 dígitos.'
WHERE origen = 'SEMILLA' AND id_producto = 'SKY_RECARGA' AND leyenda IS NULL;

-- Servicios de ejemplo adicionales (origen SEMILLA por defecto).
INSERT INTO servicio (id_servicio, nombre, categoria) VALUES
    ('TAE_ATT',    'Tiempo aire AT&T',          'TIEMPO_AIRE'),
    ('TAE_UNEFON', 'Tiempo aire Unefon',        'TIEMPO_AIRE'),
    ('TAE_BAIT',   'Tiempo aire Bait',          'TIEMPO_AIRE'),
    ('TELMEX',     'Telmex',                    'SERVICIOS'),
    ('AGUA',       'Agua potable municipal',    'SERVICIOS'),
    ('GAS_NATURAL','Gas natural',               'SERVICIOS'),
    ('IZZI',       'izzi',                      'TELEVISION'),
    ('TOTALPLAY',  'Totalplay',                 'TELEVISION')
ON CONFLICT (id_servicio) DO NOTHING;

-- Recargas: monto fijo, referencia = telefono de 10 digitos.
INSERT INTO producto (servicio_id, id_producto, nombre, tipo_monto, monto_fijo,
                      longitud_referencia, patron_referencia, comision, modalidad, leyenda)
SELECT s.id, v.id_producto, v.nombre, 'FIJO', v.monto, 10, '^[0-9]{10}$', 0, 'RECARGA', v.leyenda
FROM servicio s
JOIN (VALUES
    ('TAE_ATT',    'ATT_050', 'AT&T $50',    50.00, 'Recarga de tiempo aire AT&T para un número de 10 dígitos.'),
    ('TAE_ATT',    'ATT_100', 'AT&T $100',  100.00, 'Recarga de tiempo aire AT&T para un número de 10 dígitos.'),
    ('TAE_ATT',    'ATT_200', 'AT&T $200',  200.00, 'Recarga de tiempo aire AT&T para un número de 10 dígitos.'),
    ('TAE_UNEFON', 'UNE_050', 'Unefon $50',  50.00, 'Recarga de tiempo aire Unefon para un número de 10 dígitos.'),
    ('TAE_UNEFON', 'UNE_100', 'Unefon $100',100.00, 'Recarga de tiempo aire Unefon para un número de 10 dígitos.'),
    ('TAE_BAIT',   'BAIT_050','Bait $50',    50.00, 'Recarga Bait (Walmart) para un número de 10 dígitos.'),
    ('TAE_BAIT',   'BAIT_100','Bait $100',  100.00, 'Recarga Bait (Walmart) para un número de 10 dígitos.')
) AS v(id_servicio, id_producto, nombre, monto, leyenda) ON s.id_servicio = v.id_servicio
ON CONFLICT (servicio_id, id_producto) DO NOTHING;

-- Pagos de servicio: monto abierto con rango, multiplo y comision.
INSERT INTO producto (servicio_id, id_producto, nombre, tipo_monto, monto_minimo, monto_maximo, multiplo,
                      longitud_referencia, patron_referencia, comision, modalidad, leyenda)
SELECT s.id, v.id_producto, v.nombre, 'ABIERTO', v.minimo, v.maximo, 0.50, v.largo,
       '^[0-9]{' || v.largo || '}$', v.comision, 'PAGO_SERVICIO', v.leyenda
FROM servicio s
JOIN (VALUES
    ('TELMEX',      'TELMEX_PAGO', 'Pago de línea Telmex',   20.00, 5000.00, 10, 7.00,
        'Pago de tu línea Telmex con el teléfono de 10 dígitos.'),
    ('AGUA',        'AGUA_PAGO',   'Pago de recibo de agua', 20.00, 8000.00, 10, 6.00,
        'Pago del recibo de agua con el número de contrato de 10 dígitos.'),
    ('GAS_NATURAL', 'GAS_PAGO',    'Pago de gas natural',    20.00, 8000.00, 12, 6.00,
        'Pago del recibo de gas natural con el número de cuenta de 12 dígitos.'),
    ('IZZI',        'IZZI_PAGO',   'Pago de izzi',           50.00, 6000.00, 10, 5.00,
        'Pago de internet, TV y telefonía izzi con el número de cuenta de 10 dígitos.'),
    ('TOTALPLAY',   'TPLAY_PAGO',  'Pago de Totalplay',      50.00, 6000.00, 10, 5.00,
        'Pago de Totalplay con el número de cuenta de 10 dígitos.')
) AS v(id_servicio, id_producto, nombre, minimo, maximo, largo, comision, leyenda)
  ON s.id_servicio = v.id_servicio
ON CONFLICT (servicio_id, id_producto) DO NOTHING;
