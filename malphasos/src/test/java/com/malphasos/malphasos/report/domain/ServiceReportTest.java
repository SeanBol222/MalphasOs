package com.malphasos.malphasos.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportDeactivatedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFilledEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFinishedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportOpenedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportStandardChosenEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportStandardRemovedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportVerifiedEvent;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    private static final IntakeCheck CONFORME = new IntakeCheck(IntakeState.CONFORME, null);
    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID PUNTO = UUID.randomUUID();
    private static final UUID VERIFICACION = UUID.randomUUID();

    private static ServiceReport unReporte() {
        ServiceReport reporte = ServiceReport.open(ORDEN, EQUIPO);
        // Cerrar exige el ingreso desde el diseño único del informe; lo que se prueba aquí es lo demás.
        reporte.recordIntake(CONFORME, CONFORME, List.of());
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
        reporte.finish(UnDocumento.CUALQUIERA);
        reporte.pullEvents();

        return reporte;
    }

    private static VerificationReading unaLectura(int secuencia, String patron, String equipo) {
        return VerificationReading.of(
                VERIFICACION, PUNTO, secuencia, new BigDecimal(patron), new BigDecimal(equipo));
    }

    private static VerificationReading unaLectura(String patron, String equipo) {
        return unaLectura(1, patron, equipo);
    }

    /** Si la lectura es conforme con esa tolerancia, que desde V51 es de su verificación y no suya. */
    private static Boolean conforme(VerificationReading lectura, String tolerancia) {
        return lectura.conforme(tolerancia == null ? null : new BigDecimal(tolerancia));
    }

    /**
     * El documento con que se cierra, con la ficha de cada patrón elegido: el vencimiento que se comprueba al
     * cerrar sale de ahí desde V51.
     */
    private static ReportDocument conFichas(ReportDocument.Patron... patrones) {
        ReportDocument d = UnDocumento.CUALQUIERA;
        return new ReportDocument(d.version(), d.orden(), d.cliente(), d.equipo(), d.verificaciones(), d.empresa(),
                d.ingeniero(), d.huellaFirmaIngeniero(), d.pasos(), List.of(patrones));
    }

    private static UsedReferenceStandard unPatronQueVence(LocalDate vencimiento) {
        return new UsedReferenceStandard(UUID.randomUUID(), "Juego de pesas", "Kern", "KF1-1", "CAL-1",
                vencimiento);
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
                    VERIFICACION, null, 1, new BigDecimal("1"), new BigDecimal("1.1"));
            VerificationReading otra = VerificationReading.of(
                    VERIFICACION, null, 1, new BigDecimal("2"), new BigDecimal("2.1"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> reporte.recordVerification(List.of(una, otra)));
        }

        @Test
        @DisplayName("el mismo numero en dos puntos distintos si se admite: son dos medidas")
        void admiteElMismoNumeroEnPuntosDistintos() {
            ServiceReport reporte = unReporte();
            VerificationReading enUnPunto = unaLectura(1, "50", "50.2");
            VerificationReading enOtro = VerificationReading.of(
                    VERIFICACION, UUID.randomUUID(), 1, new BigDecimal("150"),
                    new BigDecimal("150.4"));

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
                    VERIFICACION, PUNTO, 1, null, new BigDecimal("1")));
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    VERIFICACION, PUNTO, 1, new BigDecimal("1"), null));
        }

        @Test
        @DisplayName("el numero de lectura va entre 1 y 100, el tope que la verificacion declara")
        void acotaElNumeroDeLectura() {
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    VERIFICACION, PUNTO, 0, new BigDecimal("1"), new BigDecimal("1.1")));
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    VERIFICACION, PUNTO, 101, new BigDecimal("1"), new BigDecimal("1.1")));
            assertThatCode(() -> VerificationReading.of(
                            VERIFICACION, PUNTO, 100, new BigDecimal("1"), new BigDecimal("1.1")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("un valor tiene como mucho ocho cifras enteras: lo que era numeric(12,4) es regla desde V59")
        void acotaLasCifrasDelValor() {
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    VERIFICACION, PUNTO, 1, new BigDecimal("100000000"), new BigDecimal("1")));
            assertThatIllegalArgumentException().isThrownBy(() -> VerificationReading.of(
                    VERIFICACION, PUNTO, 1, new BigDecimal("1"), new BigDecimal("-100000000")));
            assertThatCode(() -> VerificationReading.of(
                            VERIFICACION, PUNTO, 1, new BigDecimal("99999999.9999"), new BigDecimal("-99999999.9999")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("el error es equipo menos patron, con su signo")
        void error() {
            assertThat(unaLectura("100", "100.3").error()).isEqualByComparingTo("0.3");
            assertThat(unaLectura("100", "99.6").error()).isEqualByComparingTo("-0.4");
        }

        @Test
        @DisplayName("el borde de la tolerancia es conforme, por los dos lados, y un poco mas alla no")
        void bordeDeLaTolerancia() {
            // «±0,5» admite 0,5. Es el sentido en que se lee una tolerancia en un certificado.
            assertThat(conforme(unaLectura("100", "100.5"), "0.5")).isTrue();
            assertThat(conforme(unaLectura("100", "99.5"), "0.5")).isTrue();
            assertThat(conforme(unaLectura("100", "100.5001"), "0.5")).isFalse();
            assertThat(conforme(unaLectura("100", "99.4999"), "0.5")).isFalse();
        }

        @Test
        @DisplayName("sin tolerancia no dice si es conforme: no se sabe, y no se inventa")
        void sinTolerancia() {
            assertThat(conforme(unaLectura("100", "140"), null)).isNull();
        }

        @Test
        @DisplayName("admite valores negativos: un congelador se verifica a -20 grados")
        void admiteNegativos() {
            assertThatCode(() -> VerificationReading.of(
                            VERIFICACION, PUNTO, 1, new BigDecimal("-20"), new BigDecimal("-19.4")))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("el numero son los seis primeros caracteres del id, en mayusculas")
    void numero() {
        ServiceReport reporte = unReporte();

        assertThat(reporte.numero()).hasSize(6)
                .isEqualTo(reporte.getId().toString().substring(0, 6).toUpperCase());
        assertThat(ServiceReport.numeroDe(UUID.fromString("1dc6f5c8-2821-4484-bd95-db78fa514929")))
                .isEqualTo("1DC6F5");
    }

    @Nested
    @DisplayName("Los patrones de cada verificacion (V27; por verificacion desde V30)")
    class LosPatrones {

        /** Otra verificacion del mismo tipo: un termohigrometro mide temperatura y humedad. */
        private static final UUID OTRA = UUID.randomUUID();

        private static List<UUID> idsEn(ServiceReport reporte, UUID verificacion) {
            return reporte.patronesActivos().stream()
                    .filter(usado -> usado.idVerificacion().equals(verificacion))
                    .map(VerificationStandard::idEquipoPatron)
                    .toList();
        }

        @Test
        @DisplayName("ponerlo emite su evento, y ponerlo otra vez no: ni lo duplica")
        void ponerlo() {
            ServiceReport reporte = unReporte();
            UsedReferenceStandard patron = unPatronQueVence(LocalDate.now().plusYears(1));

            reporte.useReferenceStandard(VERIFICACION, patron.id());
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportStandardChosenEvent.TYPE);
            assertThat(reporte.patronesActivos()).singleElement()
                    .satisfies(usado -> assertThat(usado.idEquipoPatron()).isEqualTo(patron.id()))
                    .satisfies(usado -> assertThat(usado.idVerificacion()).isEqualTo(VERIFICACION));

            // Desde V51 no guarda la ficha: con el certificado renovado en el catálogo, se ve sin volver a ponerlo.
            reporte.useReferenceStandard(VERIFICACION, patron.id());
            assertThat(tiposDe(reporte)).isEmpty();
            assertThat(reporte.getPatrones()).hasSize(1);
        }

        @Test
        @DisplayName("una verificacion admite varios, y cada verificacion tiene los suyos")
        void variosPorVerificacion() {
            ServiceReport reporte = unReporte();
            UsedReferenceStandard pesas1 = unPatronQueVence(LocalDate.now().plusYears(1));
            UsedReferenceStandard pesas2 = unPatronQueVence(LocalDate.now().plusYears(1));

            reporte.useReferenceStandard(VERIFICACION, pesas1.id());
            reporte.useReferenceStandard(VERIFICACION, pesas2.id());
            reporte.useReferenceStandard(OTRA, pesas1.id());

            assertThat(idsEn(reporte, VERIFICACION)).containsExactly(pesas1.id(), pesas2.id());
            assertThat(idsEn(reporte, OTRA)).containsExactly(pesas1.id());
        }

        @Test
        @DisplayName("quitarlo lo retira sin borrarlo, quitarlo dos veces anuncia una, y ponerlo lo reactiva")
        void quitarYVolver() {
            ServiceReport reporte = unReporte();
            UsedReferenceStandard patron = unPatronQueVence(LocalDate.now().plusYears(1));
            reporte.useReferenceStandard(VERIFICACION, patron.id());
            UUID fila = reporte.getPatrones().getFirst().id();
            reporte.pullEvents();

            reporte.removeReferenceStandard(VERIFICACION, patron.id());
            reporte.removeReferenceStandard(VERIFICACION, patron.id());
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportStandardRemovedEvent.TYPE);
            assertThat(reporte.patronesActivos()).isEmpty();
            assertThat(reporte.getPatrones()).singleElement()
                    .satisfies(usado -> assertThat(usado.estadoActivo()).isFalse());

            // Volver a ponerlo, aun con lo mismo copiado, es un cambio: estaba quitado.
            reporte.useReferenceStandard(VERIFICACION, patron.id());
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportStandardChosenEvent.TYPE);
            assertThat(reporte.getPatrones()).singleElement().satisfies(usado -> {
                assertThat(usado.id()).isEqualTo(fila);
                assertThat(usado.estadoActivo()).isTrue();
            });
        }

        @Test
        @DisplayName("quitarlo de una verificacion no lo quita de la otra")
        void quitarDeUna() {
            ServiceReport reporte = unReporte();
            UsedReferenceStandard patron = unPatronQueVence(LocalDate.now().plusYears(1));
            reporte.useReferenceStandard(VERIFICACION, patron.id());
            reporte.useReferenceStandard(OTRA, patron.id());

            reporte.removeReferenceStandard(OTRA, patron.id());

            assertThat(idsEn(reporte, VERIFICACION)).containsExactly(patron.id());
            assertThat(idsEn(reporte, OTRA)).isEmpty();
        }

        @Test
        @DisplayName("quitar uno que nunca estuvo no hace nada")
        void quitarElQueNoEsta() {
            ServiceReport reporte = unReporte();

            reporte.removeReferenceStandard(VERIFICACION, UUID.randomUUID());

            assertThat(tiposDe(reporte)).isEmpty();
            assertThat(reporte.getPatrones()).isEmpty();
        }

        @Test
        @DisplayName("ni se pone ni se quita en un reporte cerrado, ni se pone sin verificacion o sin patron")
        void soloEnBorrador() {
            UsedReferenceStandard patron = unPatronQueVence(LocalDate.now().plusYears(1));

            assertThatIllegalStateException().isThrownBy(() -> unReporteCerrado()
                    .useReferenceStandard(VERIFICACION, patron.id()));
            assertThatIllegalStateException().isThrownBy(() -> unReporteCerrado()
                    .removeReferenceStandard(VERIFICACION, patron.id()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> unReporte().useReferenceStandard(VERIFICACION, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> unReporte().useReferenceStandard(null, patron.id()));
        }

        @Test
        @DisplayName("con lecturas y sin patron en su verificacion no se cierra")
        void lecturasSinPatron() {
            ServiceReport reporte = unReporteCerrable();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.1")));
            // Un patron en otra verificacion no cuenta: no es contra el que se tomaron estas lecturas.
            reporte.useReferenceStandard(OTRA, unPatronQueVence(LocalDate.now().plusYears(1)).id());

            assertThatIllegalStateException().isThrownBy(() -> reporte.finish(UnDocumento.CUALQUIERA))
                    .withMessageContaining("patron");
            assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
        }

        @Test
        @DisplayName("un patron quitado tampoco cuenta para cerrar")
        void quitadoNoCuenta() {
            ServiceReport reporte = unReporteCerrable();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.1")));
            UsedReferenceStandard patron = unPatronQueVence(LocalDate.now().plusYears(1));
            reporte.useReferenceStandard(VERIFICACION, patron.id());
            reporte.removeReferenceStandard(VERIFICACION, patron.id());

            assertThatIllegalStateException().isThrownBy(() -> reporte.finish(UnDocumento.CUALQUIERA));
        }

        @Test
        @DisplayName("con un patron vencido ayer no se cierra, aunque su verificacion no tenga lecturas")
        void patronVencido() {
            ServiceReport reporte = unReporteCerrable();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.1")));
            UsedReferenceStandard vigente = unPatronQueVence(LocalDate.now().plusYears(1));
            UsedReferenceStandard vencido = unPatronQueVence(LocalDate.now().minusDays(1));
            reporte.useReferenceStandard(VERIFICACION, vigente.id());
            // El informe los imprime todos: uno vencido no certifica nada, mida lo que mida.
            reporte.useReferenceStandard(OTRA, vencido.id());
            ReportDocument documento = conFichas(new ReportDocument.Patron(VERIFICACION, vigente),
                    new ReportDocument.Patron(OTRA, vencido));

            assertThatIllegalStateException().isThrownBy(() -> reporte.finish(documento))
                    .withMessageContaining("vencio");
            assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
        }

        @Test
        @DisplayName("uno vencido y quitado ya no impide cerrar")
        void vencidoQuitado() {
            ServiceReport reporte = unReporteCerrable();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.1")));
            UsedReferenceStandard vigente = unPatronQueVence(LocalDate.now().plusYears(1));
            reporte.useReferenceStandard(VERIFICACION, vigente.id());
            UsedReferenceStandard vencido = unPatronQueVence(LocalDate.now().minusDays(1));
            reporte.useReferenceStandard(VERIFICACION, vencido.id());
            reporte.removeReferenceStandard(VERIFICACION, vencido.id());

            reporte.finish(conFichas(new ReportDocument.Patron(VERIFICACION, vigente)));

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }

        @Test
        @DisplayName("un patron que vence hoy todavia vale: vence al terminar el dia")
        void patronQueVenceHoy() {
            ServiceReport reporte = unReporteCerrable();
            reporte.recordVerification(List.of(unaLectura(1, "50", "50.1")));
            UsedReferenceStandard hoy = unPatronQueVence(LocalDate.now());
            reporte.useReferenceStandard(VERIFICACION, hoy.id());

            reporte.finish(conFichas(new ReportDocument.Patron(VERIFICACION, hoy)));

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }

        @Test
        @DisplayName("sin lecturas no hace falta patron")
        void sinLecturasNoHaceFalta() {
            ServiceReport reporte = unReporteCerrable();

            reporte.finish(UnDocumento.CUALQUIERA);

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }
    }

    @Nested
    @DisplayName("Al cerrarlo")
    class AlCerrarlo {

        @Test
        @DisplayName("queda finalizado, con la fecha en que se cerro")
        void quedaFinalizado() {
            ServiceReport reporte = unReporteCerrable();

            reporte.finish(UnDocumento.CUALQUIERA);

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
            assertThat(reporte.getFinalizado()).isNotNull();
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportFinishedEvent.TYPE);
        }

        @Test
        @DisplayName("sin procedimientos no se cierra: es lo que se hizo")
        void exigeProcedimientos() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, null, null, ServiceResult.OPERATIVO);

            assertThatIllegalStateException().isThrownBy(() -> reporte.finish(UnDocumento.CUALQUIERA))
                    .withMessageContaining("procedimientos");
        }

        @Test
        @DisplayName("sin resultado no se cierra: de ese dato cuelgan la hoja de vida y las alertas")
        void exigeResultado() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, null);

            assertThatIllegalStateException().isThrownBy(() -> reporte.finish(UnDocumento.CUALQUIERA))
                    .withMessageContaining("resultado");
        }

        @Test
        @DisplayName("unos procedimientos en blanco no cuentan como procedimientos")
        void losProcedimientosEnBlancoNoCuentan() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "   ", null, ServiceResult.OPERATIVO);

            assertThatIllegalStateException().isThrownBy(() -> reporte.finish(UnDocumento.CUALQUIERA));
        }

        @Test
        @DisplayName("un reporte cerrado no se vuelve a cerrar")
        void noSeCierraDosVeces() {
            ServiceReport cerrado = unReporteCerrado();

            assertThatIllegalStateException().isThrownBy(() -> cerrado.finish(UnDocumento.CUALQUIERA));
        }

        @Test
        @DisplayName("sin falla ni diagnostico se cierra: desde el 2026-10-27 no los exige ningun cierre")
        void sinFallaNiDiagnostico() {
            // Un correctivo los exigia desde el 2026-10-14, con su propio cierre; el usuario los quito.
            ServiceReport reporte = unReporteCerrable();
            reporte.fill(null, null, null, null, null);

            reporte.finish(UnDocumento.CUALQUIERA);

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
            assertThat(reporte.getFallaReportada()).isNull();
            assertThat(reporte.getDiagnostico()).isNull();
        }

        @Test
        @DisplayName("la causa y los repuestos se guardan, y un blanco los borra")
        void causaYRepuestos() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, null, null, " Sobretension ", "Fusible 2 A", null);

            assertThat(reporte.getCausa()).isEqualTo("Sobretension");
            assertThat(reporte.getRepuestos()).isEqualTo("Fusible 2 A");
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportFilledEvent.TYPE);

            reporte.fill(null, null, null, null, "  ", null, null);
            assertThat(reporte.getCausa()).isNull();
            assertThat(reporte.getRepuestos()).isEqualTo("Fusible 2 A");
        }

        @Test
        @DisplayName("los repuestos salen en la linea de la hoja de vida, detras de lo que se hizo")
        void repuestosEnLaHojaDeVida() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Cambio de fusible", null, null, "Fusible 2 A", null);

            assertThat(reporte.descripcionDelServicio()).isEqualTo("Cambio de fusible Repuestos: Fusible 2 A.");
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
