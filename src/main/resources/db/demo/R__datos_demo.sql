-- ===========================================================================
-- Datos de demostracion (solo perfil "demo"). Contrasena de todos: Demo-2026!
-- Es repetible e idempotente: se puede ejecutar varias veces sin duplicar.
-- ===========================================================================

INSERT INTO cliente (usuario, password_hash, nombre, activo, pagos_habilitados) VALUES
    ('ana',    '$2a$10$rRzneqw2Atxi6W8nXTYqaei.fzPVCZJB1cc9.XuNHDieWtCKixCai', 'Ana Lopez',     TRUE,  TRUE),
    ('bruno',  '$2a$10$rRzneqw2Atxi6W8nXTYqaei.fzPVCZJB1cc9.XuNHDieWtCKixCai', 'Bruno Ramirez', TRUE,  TRUE),
    ('carla',  '$2a$10$rRzneqw2Atxi6W8nXTYqaei.fzPVCZJB1cc9.XuNHDieWtCKixCai', 'Carla Mendez',  TRUE,  FALSE)
ON CONFLICT (usuario) DO NOTHING;

INSERT INTO cuenta (cliente_id, numero, saldo, estado, limite_diario)
SELECT c.id, v.numero, v.saldo, v.estado, v.limite
FROM cliente c
JOIN (VALUES
    ('ana',   '1000000001', 1500.00, 'ACTIVA',    5000.00),
    ('ana',   '1000000002',  300.00, 'BLOQUEADA', 5000.00),
    ('bruno', '2000000001',   80.00, 'ACTIVA',    5000.00),
    ('carla', '3000000001', 1000.00, 'ACTIVA',    5000.00)
) AS v(usuario, numero, saldo, estado, limite) ON c.usuario = v.usuario
ON CONFLICT (numero) DO NOTHING;
