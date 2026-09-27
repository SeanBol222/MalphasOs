package com.malphasos.malphasos.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportDeactivatedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFilledEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFinishedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportOpenedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportVerifiedEvent;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Invariantes del agregado de reportes de servicio, sin base de datos.
 *
 * <p>Se prueba lo que el reporte puede decidir con lo que tiene delante. Que la orden exista y esté
 * activa, que el equipo esté en su alcance y que el tipo de ese equipo se verifique de la forma que
 * dicen las lecturas exigen consultar otros módulos, y se verificarán con el servicio de aplicación.
 */
class ServiceReportTest {

    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID PUNTO = UUID.randomUUID();

    private static ServiceReport unReporte() {
        ServiceReport reporte = ServiceReport.open(ORDEN, EQUIPO);
        reporte.pullEvents();

        return reporte;
    }

    /** Un reporte con lo mínimo para poder cerrarse. */
    private static ServiceReport unReporteCerrable() {
        ServiceReport reporte = unReporte();
        reporte.fill(null, null, "Limpieza y calibracion", null, ServiceResult.OPERATIVO);
        reporte.pullEvents();

        return reporte;
    }

    private static ServiceReport unReporteCerrado() {
        ServiceReport reporte = unReporteCerrable();
        reporte.finish();
        reporte.pullEvents();

        return reporte;
    }

    private static VerificationReading unaLectura(int secuencia, String patron, String equipo) {
        return VerificationReading.of(
                PUNTO, secuencia, new BigDecimal(patron), new BigDecimal(equipo), "mmHg");
    }

    private static List<String> tiposDe(ServiceReport reporte) {
        return reporte.pullEvents().stream()
                .map(DomainEvent::metadata)
                .map(metadata -> metadata.eventType())
                .toList();
    }

    @Nested
    @DisplayName("Al abrirlo")
    class AlAbrirlo {

        @Test
        @DisplayName("nace en borrador, vacio y sin lecturas")
        void naceEnBorradorYVacio() {
            ServiceReport reporte = ServiceReport.open(ORDEN, EQUIPO);

            assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
            assertThat(reporte.getFallaReportada()).isNull();
            assertThat(reporte.getDiagnostico()).isNull();
            assertThat(reporte.getProcedimientos()).isNull();
            assertThat(reporte.getObservaciones()).isNull();
            assertThat(reporte.getResultado()).isNull();
            assertThat(reporte.getFinalizado()).isNull();
            assertThat(reporte.lecturasActivas()).isEmpty();
            assertThat(reporte.isEstadoActivo()).isTrue();
        }

        @Test
        @DisplayName("anuncia que se abrio el reporte de un equipo")
        void anunciaElAlta() {
            assertThat(tiposDe(ServiceReport.open(ORDEN, EQUIPO)))
                    .containsExactly(ServiceReportOpenedEvent.TYPE);
        }

        @Test
        @DisplayName("la orden y el equipo son obligatorios")
        void exigeOrdenYEquipo() {
            assertThatIllegalArgumentException().isThrownBy(() -> ServiceReport.open(null, EQUIPO));
            assertThatIllegalArgumentException().isThrownBy(() -> ServiceReport.open(ORDEN, null));
        }

        @Test
        @DisplayName("rehidratar no emite ningun evento: leer de la base no es un hecho del dominio")
        void rehidratarNoEmite() {
            ServiceReport reporte = ServiceReport.rehydrate(
                    UUID.randomUUID(), ORDEN, EQUIPO, ReportState.FINALIZADO, null, null,
                    "Limpieza", null, ServiceResult.OPERATIVO, LocalDateTime.now(), List.of(), true);

            assertThat(reporte.hasPendingEvents()).isFalse();
        }
    }

    @Nested
    @DisplayName("Al llenar la informacion tecnica")
    class AlLlenarlo {

        @Test
        @DisplayName("guarda los cinco campos de RF-15")
        void guardaLosCincoCampos() {
            ServiceReport reporte = unReporte();

            reporte.fill("No enciende", "Fuente quemada", "Cambio de fuente", "Queda en prueba",
                    ServiceResult.OPERATIVO_CON_RESTRICCIONES);

            assertThat(reporte.getFallaReportada()).isEqualTo("No enciende");
            assertThat(reporte.getDiagnostico()).isEqualTo("Fuente quemada");
            assertThat(reporte.getProcedimientos()).isEqualTo("Cambio de fuente");
            assertThat(reporte.getObservaciones()).isEqualTo("Queda en prueba");
            assertThat(reporte.getResultado()).isEqualTo(ServiceResult.OPERATIVO_CON_RESTRICCIONES);
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportFilledEvent.TYPE);
        }

        @Test
        @DisplayName("un nulo deja el campo como estaba")
        void elNuloNoBorra() {
            ServiceReport reporte = unReporte();
            reporte.fill("No enciende", null, null, null, null);
            reporte.pullEvents();

            reporte.fill(null, "Fuente quemada", null, null, null);

            assertThat(reporte.getFallaReportada()).isEqualTo("No enciende");
            assertThat(reporte.getDiagnostico()).isEqualTo("Fuente quemada");
        }

        @Test
        @DisplayName("un texto en blanco si borra: es como se corrige lo escrito por error")
        void elBlancoBorra() {
            ServiceReport reporte = unReporte();
            reporte.fill("No enciende", null, null, null, null);
            reporte.pullEvents();

            reporte.fill("   ", null, null, null, null);

            assertThat(reporte.getFallaReportada()).isNull();
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportFilledEvent.TYPE);
        }

        @Test
        @DisplayName("el texto se recorta")
        void recortaElTexto() {
            ServiceReport reporte = unReporte();

            reporte.fill("  No enciende  ", null, null, null, null);

            assertThat(reporte.getFallaReportada()).isEqualTo("No enciende");
        }

        @Test
        @DisplayName("escribir lo mismo otra vez no emite evento")
        void loMismoNoEmite() {
            ServiceReport reporte = unReporte();
            reporte.fill("No enciende", null, null, null, ServiceResult.OPERATIVO);
            reporte.pullEvents();

            reporte.fill("No enciende", null, null, null, ServiceResult.OPERATIVO);

            assertThat(reporte.hasPendingEvents()).isFalse();
        }

        @Test
        @DisplayName("no se llena un reporte ya cerrado")
        void noSeLlenaUnReporteCerrado() {
            ServiceReport cerrado = unReporteCerrado();

            assertThatIllegalStateException()
                    .isThrownBy(() -> cerrado.fill("Tarde", null, null, null, null))
                    .withMessageContaining("retirarlo y abrir otro");
        }

        @Test
        @DisplayName("no se llena un reporte retirado")
        void noSeLlenaUnReporteRetirado() {
            ServiceReport reporte = unReporte();
            reporte.deactivate();

            assertThatIllegalStateException()
                    .isThrownBy(() -> reporte.fill("Tarde", null, null, null, null))
                    .withMessageContaining("retirado");
        }
    }

    @Nested
    @DisplayName("Al registrar la verificacion")
    class AlVerificar {

        @Test
        @DisplayName("guarda las lecturas y anuncia que se verifico")
        void guardaLasLecturas() {
            ServiceReport reporte = unReporte();

            reporte.recordVerification(List.of(unaLectura(1, "50", "50.2"), unaLectura(2, "50", "49.8")));

            assertThat(reporte.lecturasActivas()).hasSize(2);
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportVerifiedEvent.TYPE);
        }

        @Test
        @DisplayName("volver a registrar retira las anteriores en vez de borrarlas")
        void retiraLasAnteriores() {
            ServiceReport reporte = unReporte();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.2")));
            reporte.pullEvents();

            reporte.recordVerification(List.of(unaLectura(1, "50", "51.9")));

            assertThat(reporte.lecturasActivas()).hasSize(1);
            assertThat(reporte.lecturasActivas().getFirst().valorEquipo())
                    .isEqualByComparingTo(new BigDecimal("51.9"));
            // La corregida sigue ahi, retirada: aqui nada se borra.
            assertThat(reporte.getLecturas()).hasSize(2);
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportVerifiedEvent.TYPE);
        }

        @Test
        @DisplayName("registrar exactamente las mismas lecturas no emite evento")
        void lasMismasNoEmiten() {
            ServiceReport reporte = unReporte();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.2"), unaLectura(2, "50", "49.8")));
            reporte.pullEvents();

            reporte.recordVerification(List.of(unaLectura(2, "50", "49.8"), unaLectura(1, "50", "50.2")));

            assertThat(reporte.hasPendingEvents()).isFalse();
            assertThat(reporte.getLecturas()).hasSize(2);
        }

        @Test
        @DisplayName("la escala no hace una lectura nueva: 50 y 50.0000 se normalizan igual")
        void laEscalaNoHaceUnaLecturaNueva() {
            // Lo que esta prueba ejerce es la NORMALIZACION de VerificationReading, no la comparacion
            // numerica: se comprobo cambiando compareTo por equals y siguio verde. Queda escrito para
            // que nadie la lea como la prueba de compareTo, que hoy no tiene ninguna.
            ServiceReport reporte = unReporte();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.2")));
            reporte.pullEvents();

            reporte.recordVerification(List.of(unaLectura(1, "50.0000", "50.2000")));

            assertThat(reporte.hasPendingEvents()).isFalse();
        }

        @Test
        @DisplayName("dos lecturas en el mismo sitio se rechazan: mismo punto y mismo numero")
        void rechazaDosLecturasEnElMismoSitio() {
            ServiceReport reporte = unReporte();

            assertThatIllegalArgumentException().isThrownBy(() -> reporte.recordVerification(
                            List.of(unaLectura(1, "50", "50.2"), unaLectura(1, "50", "49.9"))))
                    .withMessageContaining("dos veces");
        }

        @Test
        @DisplayName("sin punto tampoco se repite el numero: es la modalidad de patron y equipo variables")
        void rechazaDosLecturasSinPuntoConElMismoNumero() {
            ServiceReport reporte = unReporte();
            VerificationReading una = VerificationReading.of(
                    null, 1, new BigDecimal("1"), new BigDecimal("1.1"), "mA");
            VerificationReading otra = VerificationReading.of(
                    null, 1, new BigDecimal("2"), new BigDecimal("2.1"), "mA");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> reporte.recordVerification(List.of(una, otra)));
        }

        @Test
        @DisplayName("el mismo numero en dos puntos distintos si se admite: son dos medidas")
        void admiteElMismoNumeroEnPuntosDistintos() {
            ServiceReport reporte = unReporte();
            VerificationReading enUnPunto = unaLectura(1, "50", "50.2");
            VerificationReading enOtro = VerificationReading.of(
                    UUID.randomUUID(), 1, new BigDecimal("150"), new BigDecimal("150.4"), "mmHg");

            assertThatCode(() -> reporte.recordVerification(List.of(enUnPunto, enOtro)))
                    .doesNotThrowAnyException();
            assertThat(reporte.lecturasActivas()).hasSize(2);
        }

        @Test
        @DisplayName("registrar una verificacion sin lecturas no es registrar nada")
        void rechazaLaListaVacia() {
            ServiceReport reporte = unReporte();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> reporte.recordVerification(List.of()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> reporte.recordVerification(null));
        }

        @Test
        @DisplayName("no se verifica un reporte ya cerrado")
        void noSeVerificaUnReporteCerrado() {
            ServiceReport cerrado = unReporteCerrado();

            assertThatIllegalStateException().isThrownBy(
                    () -> cerrado.recordVerification(List.of(unaLectura(1, "50", "50.2"))));
        }

        @Test
        @DisplayName("las lecturas se entregan como copia: modificarla no cambia el reporte")
        void lasLecturasSonCopia() {
            ServiceReport reporte = unReporte();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.2")));

            assertThatThrownBy(() -> reporte.getLecturas().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(reporte.lecturasActivas()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("La lectura como pieza")
    class LaLectura {

        @Test
        @DisplayName("exige los dos valores: el reporte imprime las dos columnas")
        void exigeLosDosValores() {
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    PUNTO, 1, null, new BigDecimal("1"), "mA"));
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    PUNTO, 1, new BigDecimal("1"), null, "mA"));
        }

        @Test
        @DisplayName("exige la unidad: un numero sin unidad no se puede imprimir")
        void exigeLaUnidad() {
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    PUNTO, 1, new BigDecimal("1"), new BigDecimal("1.1"), "  "));
        }

        @Test
        @DisplayName("el numero de lectura va entre 1 y 100, el tope que V8 declara")
        void acotaElNumeroDeLectura() {
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    PUNTO, 0, new BigDecimal("1"), new BigDecimal("1.1"), "mA"));
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    PUNTO, 101, new BigDecimal("1"), new BigDecimal("1.1"), "mA"));
            assertThatCode(() -> VerificationReading.of(
                            PUNTO, 100, new BigDecimal("1"), new BigDecimal("1.1"), "mA"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("admite valores negativos: un congelador se verifica a -20 grados")
        void admiteNegativos() {
            assertThatCode(() -> VerificationReading.of(
                            PUNTO, 1, new BigDecimal("-20"), new BigDecimal("-19.4"), "C"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Al cerrarlo")
    class AlCerrarlo {

        @Test
        @DisplayName("queda finalizado, con la fecha en que se cerro")
        void quedaFinalizado() {
            ServiceReport reporte = unReporteCerrable();

            reporte.finish();

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
            assertThat(reporte.getFinalizado()).isNotNull();
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportFinishedEvent.TYPE);
        }

        @Test
        @DisplayName("sin procedimientos no se cierra: es lo que se hizo")
        void exigeProcedimientos() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, null, null, ServiceResult.OPERATIVO);

            assertThatIllegalStateException().isThrownBy(reporte::finish)
                    .withMessageContaining("procedimientos");
        }

        @Test
        @DisplayName("sin resultado no se cierra: de ese dato cuelgan la hoja de vida y las alertas")
        void exigeResultado() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, null);

            assertThatIllegalStateException().isThrownBy(reporte::finish)
                    .withMessageContaining("resultado");
        }

        @Test
        @DisplayName("unos procedimientos en blanco no cuentan como procedimientos")
        void losProcedimientosEnBlancoNoCuentan() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "   ", null, ServiceResult.OPERATIVO);

            assertThatIllegalStateException().isThrownBy(reporte::finish);
        }

        @Test
        @DisplayName("un reporte cerrado no se vuelve a cerrar")
        void noSeCierraDosVeces() {
            ServiceReport cerrado = unReporteCerrado();

            assertThatIllegalStateException().isThrownBy(cerrado::finish);
        }

        @Test
        @DisplayName("no existe ninguna operacion para reabrir un reporte, y es a proposito")
        void noHayOperacionParaReabrirlo() {
            // Un reporte cerrado es lo que se entrego al cliente, y de el cuelgan la hoja de vida y las
            // alertas. Esta prueba existe para que anadir un reopen() sea una decision y no un descuido.
            List<String> operaciones = java.util.Arrays.stream(ServiceReport.class.getDeclaredMethods())
                    .filter(metodo -> java.lang.reflect.Modifier.isPublic(metodo.getModifiers()))
                    .map(java.lang.reflect.Method::getName)
                    .filter(nombre -> nombre.contains("reopen") || nombre.contains("reabrir"))
                    .toList();

            assertThat(operaciones).isEmpty();
            assertThat(ReportState.FINALIZADO.esFinal()).isTrue();
        }
    }

    @Nested
    @DisplayName("Al retirarlo")
    class AlRetirarlo {

        @Test
        @DisplayName("queda inactivo y lo anuncia")
        void quedaInactivo() {
            ServiceReport reporte = unReporte();

            reporte.deactivate();

            assertThat(reporte.isEstadoActivo()).isFalse();
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportDeactivatedEvent.TYPE);
        }

        @Test
        @DisplayName("retirarlo dos veces no emite dos eventos")
        void esIdempotente() {
            ServiceReport reporte = unReporte();
            reporte.deactivate();
            reporte.pullEvents();

            reporte.deactivate();

            assertThat(reporte.hasPendingEvents()).isFalse();
        }

        @Test
        @DisplayName("un reporte cerrado si se puede retirar: es la unica salida de un error")
        void unReporteCerradoSeRetira() {
            ServiceReport cerrado = unReporteCerrado();

            cerrado.deactivate();

            assertThat(cerrado.isEstadoActivo()).isFalse();
            assertThat(cerrado.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }
    }
}
