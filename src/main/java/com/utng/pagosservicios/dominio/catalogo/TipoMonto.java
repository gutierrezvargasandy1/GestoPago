package com.utng.pagosservicios.dominio.catalogo;

public enum TipoMonto {
    /** El producto vale exactamente su monto fijo; el cliente no elige cuanto. */
    FIJO,
    /** El cliente elige el monto dentro de un rango y respetando un multiplo. */
    ABIERTO
}
