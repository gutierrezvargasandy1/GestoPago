package com.utng.pagosservicios.dominio.catalogo;

/** De donde viene un registro del catalogo. */
public enum OrigenCatalogo {

    /**
     * Catalogo de ejemplo cargado por migracion. Sus identificadores solo los
     * reconoce el simulador local, por eso la aplicacion lo oculta salvo que
     * pagos.catalogo.permitir-ejemplo=true (perfil simulador y pruebas).
     */
    SEMILLA,

    /** Sincronizado desde getProductList de PuntoRed. */
    PROVEEDOR
}
