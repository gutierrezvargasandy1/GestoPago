-- ===========================================================================
-- V2: Catalogo semilla.
--
-- Refleja la estructura del catalogo del proveedor (getProductList.do). Los
-- identificadores son de ejemplo y deben sincronizarse con el catalogo oficial.
-- ===========================================================================

INSERT INTO servicio (id_servicio, nombre, categoria) VALUES
    ('TAE_TELCEL',   'Tiempo aire Telcel',               'TIEMPO_AIRE'),
    ('TAE_MOVISTAR', 'Tiempo aire Movistar',             'TIEMPO_AIRE'),
    ('CFE',          'Comisión Federal de Electricidad', 'SERVICIOS'),
    ('SKY',          'Sky',                              'TELEVISION');

-- Tiempo aire: monto fijo. El cliente elige el producto, no el monto.
INSERT INTO producto (servicio_id, id_producto, nombre, tipo_monto, monto_fijo,
                      longitud_referencia, patron_referencia, comision)
SELECT s.id, v.id_producto, v.nombre, 'FIJO', v.monto, 10, '^[0-9]{10}$', 0
FROM servicio s
JOIN (VALUES
    ('TAE_TELCEL',   'TEL_020', 'Telcel $20',     20.00),
    ('TAE_TELCEL',   'TEL_050', 'Telcel $50',     50.00),
    ('TAE_TELCEL',   'TEL_100', 'Telcel $100',   100.00),
    ('TAE_TELCEL',   'TEL_200', 'Telcel $200',   200.00),
    ('TAE_MOVISTAR', 'MOV_030', 'Movistar $30',   30.00),
    ('TAE_MOVISTAR', 'MOV_100', 'Movistar $100', 100.00)
) AS v(id_servicio, id_producto, nombre, monto) ON s.id_servicio = v.id_servicio;

-- Servicios con monto abierto: rango, multiplo y comision.
INSERT INTO producto (servicio_id, id_producto, nombre, tipo_monto, monto_minimo, monto_maximo,
                      multiplo, longitud_referencia, patron_referencia, comision)
SELECT s.id, 'CFE_PAGO', 'Pago de recibo CFE', 'ABIERTO', 10.00, 10000.00, 0.50, 12, '^[0-9]{12}$', 8.00
FROM servicio s WHERE s.id_servicio = 'CFE';

INSERT INTO producto (servicio_id, id_producto, nombre, tipo_monto, monto_minimo, monto_maximo,
                      multiplo, longitud_referencia, patron_referencia, comision)
SELECT s.id, 'SKY_RECARGA', 'Recarga Sky', 'ABIERTO', 50.00, 5000.00, 1.00, 11, '^[0-9]{11}$', 5.00
FROM servicio s WHERE s.id_servicio = 'SKY';

-- Producto que existe pero esta desactivado: debe rechazarse aunque este en el catalogo.
INSERT INTO producto (servicio_id, id_producto, nombre, tipo_monto, monto_fijo,
                      longitud_referencia, patron_referencia, comision, activo)
SELECT s.id, 'TEL_500', 'Telcel $500 (descontinuado)', 'FIJO', 500.00, 10, '^[0-9]{10}$', 0, FALSE
FROM servicio s WHERE s.id_servicio = 'TAE_TELCEL';
