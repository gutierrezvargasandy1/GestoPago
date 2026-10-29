package com.utng.pagosservicios.web;

import com.utng.pagosservicios.servicio.CatalogoService;
import com.utng.pagosservicios.web.dto.ProductoResponse;
import com.utng.pagosservicios.web.dto.ServicioResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Permite al cliente conocer servicios, productos y reglas de monto antes de pagar. */
@RestController
@RequestMapping("/api/v1/catalogo")
public class CatalogoController {

    private final CatalogoService catalogo;

    public CatalogoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/servicios")
    public List<ServicioResponse> servicios() {
        return catalogo.serviciosDisponibles().stream()
                .map(ServicioResponse::de)
                .toList();
    }

    @GetMapping("/servicios/{idServicio}/productos")
    public List<ProductoResponse> productos(@PathVariable("idServicio") String idServicio) {
        return catalogo.productosDisponibles(idServicio).stream()
                .map(ProductoResponse::de)
                .toList();
    }
}
