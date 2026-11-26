package com.utng.pagosservicios.servicio;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Terminos y condiciones de uso. Viven en el servidor para que la app web y la
 * movil muestren exactamente el mismo texto, y cada cliente queda registrado con
 * la version que acepto al crear su cuenta.
 */
@Service
public class TerminosService {

    public static final String VERSION = "1.0";
    public static final LocalDate VIGENTES_DESDE = LocalDate.of(2026, 11, 26);

    public record Seccion(String titulo, String texto) {
    }

    public record Terminos(String version, LocalDate vigentesDesde, List<Seccion> secciones) {
    }

    private static final List<Seccion> SECCIONES = List.of(
            new Seccion("1. Quiénes somos",
                    "GestoPago es un prototipo académico desarrollado en la Universidad Tecnológica del Norte de "
                            + "Guanajuato (UTNG) para la asignatura de Desarrollo de Aplicaciones Móviles. Permite "
                            + "consultar un catálogo de recargas y pagos de servicios y comprarlos desde la web o "
                            + "desde la app móvil con la misma cuenta."),
            new Seccion("2. Tu cuenta",
                    "Te registras con tu correo electrónico y una contraseña de al menos 8 caracteres con letras "
                            + "y números. Eres responsable de mantener en secreto tu contraseña y el dispositivo "
                            + "donde tienes tu app autenticadora. Los datos que proporciones deben ser verdaderos."),
            new Seccion("3. Seguridad y sesiones",
                    "Para entrar se pide un segundo factor: un código de 6 dígitos de una app autenticadora, "
                            + "que se valida en el servidor. Solo puedes tener una sesión activa a la vez, en la web "
                            + "o en el celular: si entras desde otra plataforma se te pedirá cerrar la anterior. La "
                            + "sesión se cierra sola tras 30 minutos sin actividad o 8 horas en total."),
            new Seccion("4. Compras y sus estados",
                    "Antes de cobrar te mostramos el producto, la referencia y el total, y te pedimos "
                            + "confirmarlo. Una compra puede quedar: exitosa (el proveedor la confirmó), rechazada "
                            + "(no se realizó y el dinero se devuelve a tu cuenta), pendiente (el proveedor no dio una "
                            + "respuesta definitiva; el monto queda apartado hasta confirmarla o devolverlo) o con "
                            + "error de comunicación (no se pudo contactar al proveedor y no se te cobra). Solo las "
                            + "compras exitosas cuentan como compras realizadas."),
            new Seccion("5. Saldo, comisiones y límites",
                    "Las compras se cobran del saldo de tu cuenta en GestoPago. Algunos pagos de servicio tienen "
                            + "una comisión que se muestra antes de confirmar. Cada cuenta tiene un límite de compras "
                            + "por día."),
            new Seccion("6. Modo demostración",
                    "Cuando el proveedor GestoPago no está disponible, la plataforma puede operar en modo "
                            + "demostración: el catálogo de ejemplo y el resultado de las compras los genera el propio "
                            + "sistema y no se realiza ninguna recarga ni pago real. La app lo indica con un aviso "
                            + "visible."),
            new Seccion("7. Tus datos",
                    "Guardamos tu nombre, tu correo, la contraseña cifrada (nunca en texto plano), la clave de tu "
                            + "doble factor, el registro de tus sesiones y tus operaciones. Se usan solo para operar "
                            + "tu cuenta y no se comparten con terceros, salvo los datos de cada compra que necesita "
                            + "el proveedor para procesarla. Puedes consultar y editar tu nombre, correo y contraseña "
                            + "desde la sección Cuenta."),
            new Seccion("8. Uso permitido",
                    "No está permitido usar la plataforma para fines ilícitos, intentar acceder a cuentas ajenas ni "
                            + "alterar su funcionamiento. Podemos suspender cuentas que incumplan estos términos."),
            new Seccion("9. Cambios a estos términos",
                    "Si estos términos cambian se publicará una nueva versión con su fecha. La versión que "
                            + "aceptaste queda registrada en tu cuenta."));

    public Terminos vigentes() {
        return new Terminos(VERSION, VIGENTES_DESDE, SECCIONES);
    }
}
