package com.malphasos.malphasos.report.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.infrastructure.output.BrandPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.EquipmentPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.EquipmentTypePersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.ManufacturerPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.MetrologyCatalogPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.ModelPersistenceAdapter;
import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.application.model.WorkOrderReportProgress;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.shared.infrastructure.output.image.DocumentImageStoreAdapter;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.ReceiptSignature;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.infrastructure.output.WorkOrderPersistenceAdapter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * La persistencia de los reportes de servicio contra un PostgreSQL real.
 *
 * <p><b>Por qué existe esta clase, escrita a la vez que el adaptador y no cuatro tandas después.</b>
 * {@code ServiceReportServiceTest} usa un doble del puerto, de modo que sin esto nada ejercería el
 * mapper. Y ahí no vive un mapeo trivial: vive la conciliación de las lecturas, donde una lectura
 * corregida <b>pasa a inactiva sin desaparecer</b> y la nueva se inserta al lado. En
 * {@code work-order} esa ausencia duró cuatro tandas y destapó un adaptador sin
 * {@code @Transactional} que era inservible por su cuenta; aquí se cubre desde el primer día.
 *
 * <p>Las comprobaciones van contra la tabla con SQL directo a propósito. Preguntarle al agregado
 * cuántas lecturas tiene lo contestaría el mapper, que es justo la pieza bajo prueba.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM dato_verificacion",
            "DELETE FROM paso_reporte",
            "DELETE FROM patron_verificacion_reporte",
            "DELETE FROM accesorio_reporte",
            "DELETE FROM protocolo",
            "DELETE FROM reporte_servicio",
            "DELETE FROM imagen_documento",
            "DELETE FROM equipo_patron",
            "DELETE FROM orden_trabajo_equipo",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM punto_verificacion",
            "DELETE FROM verificacion_tipo_equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM marca",
            "DELETE FROM fabricante",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente",
            "DELETE FROM ciudad",
            "DELETE FROM pais",
            "DELETE FROM persona"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ServiceReportPersistenceAdapterTest {

    @Autowired private ServiceReportPersistenceAdapter adapter;
    @Autowired private WorkOrderPersistenceAdapter workOrderAdapter;
    @Autowired private ManufacturerPersistenceAdapter manufacturerAdapter;
    @Autowired private BrandPersistenceAdapter brandAdapter;
    @Autowired private EquipmentTypePersistenceAdapter equipmentTypeAdapter;
    @Autowired private EquipmentPersistenceAdapter equipmentAdapter;
    @Autowired private ModelPersistenceAdapter modelAdapter;
    @Autowired private MetrologyCatalogPersistenceAdapter metrologyAdapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------------------
    // Andamiaje: un reporte no existe sin un equipo dentro de una orden
    // ---------------------------------------------------------------------------

    private String unico() {
        return String.valueOf(System.nanoTime());
    }

    private UUID unCliente() {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', 'Hospital')
                """,
                cliente, String.valueOf(System.nanoTime() % 10_000_000_000L));

        return cliente;
    }

    private UUID unaSedeDe(UUID cliente) {
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        UUID sede = UUID.randomUUID();
        long n = System.nanoTime();
        String iso = CodigoIsoLibre.en(jdbcTemplate);

        jdbcTemplate.update(
                "INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                pais, iso, "Pais " + n);
        jdbcTemplate.update(
                "INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Ciudad " + n, pais);
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero,
                                  k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30-40', ?, ?)
                """,
                sede, "Sede " + n, cliente, ciudad);

        return sede;
    }

    private UUID unAreaDe(UUID sede) {
        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, "Area " + unico(), sede);

        return area;
    }

    /**
     * Un tipo verificable en un punto, para tener una verificación y un punto reales a los que apuntar.
     *
     * <p>La magnitud y la unidad se <b>leen del catálogo sembrado por {@code V10}</b>: no se pueden
     * inventar, porque el esquema ata el par (magnitud, unidad) con una foránea compuesta.
     */
    private EquipmentType unTipoVerificable() {
        Magnitude presion = metrologyAdapter.findAllMagnitudes().stream()
                .filter(m -> m.codigo().equals("presion"))
                .findFirst()
                .orElseThrow();
        MeasurementUnit mmhg = metrologyAdapter.findUnitsByMagnitude(presion.id()).stream()
                .filter(u -> u.simbolo().equals("mmHg"))
                .findFirst()
                .orElseThrow();

        return equipmentTypeAdapter.save(EquipmentType.create(
                "Tipo " + unico(), "Definicion", "Cuidados", "Electronica", null, null,
                List.of(TypeVerification.of(presion, mmhg, VerificationMode.PATRON_CONSTANTE, 2,
                        List.of(VerificationPoint.of(new BigDecimal("50"))))),
                100_000L));
    }

    private UUID unModeloDe(EquipmentType tipo) {
        Manufacturer fabricante =
                manufacturerAdapter.save(Manufacturer.create("Draeger " + unico(), null));
        Brand marca = brandAdapter.save(Brand.create("Marca " + unico()));
        Equipment asociacion = equipmentAdapter.save(Equipment.create(tipo.getId(), marca.getId()));

        return modelAdapter.save(Model.create("Modelo " + unico(), null, fabricante.getId(), asociacion.getId())).getId();
    }

    private UUID unEquipoEn(UUID area, UUID modelo) {
        UUID unidad = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)
                VALUES (?, ?, ?, ?)
                """,
                unidad, "SN-" + unico(), modelo, area);

        return unidad;
    }

    /** Un equipo dentro del alcance de una orden guardada: el único destino que el reporte admite. */
    private Alcance unAlcance() {
        EquipmentType tipo = unTipoVerificable();
        UUID cliente = unCliente();
        UUID sede = unaSedeDe(cliente);
        UUID area = unAreaDe(sede);
        UUID equipo = unEquipoEn(area, unModeloDe(tipo));

        WorkOrder orden = WorkOrder.schedule(
                cliente, sede, LocalDate.of(2026, 10, 1), Periodicity.ANUAL, ServiceType.PREVENTIVO);
        orden.addEquipment(equipo, area);
        workOrderAdapter.save(orden);

        TypeVerification verificacion = tipo.verificacionesActivas().getFirst();

        return new Alcance(orden.getId(), equipo, verificacion.id(),
                verificacion.puntosActivos().getFirst().id());
    }

    /**
     * Lo que hace falta nombrar para abrir un reporte y ponerle lecturas.
     *
     * <p>Lleva la verificación desde el 2026-10-03: una lectura ya no se identifica solo por su punto,
     * porque con dos magnitudes variables el punto nulo no distingue nada.
     */
    private record Alcance(UUID orden, UUID equipo, UUID verificacion, UUID punto) {
    }

    /** Las filas de lecturas de un reporte, activas e inactivas, que es lo que la tabla guarda. */
    private Integer filasDeLecturas(UUID reporte) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dato_verificacion WHERE k_id_reporte_servicio = ?",
                Integer.class, reporte);
    }

    private Integer filasActivasDeLecturas(UUID reporte) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM dato_verificacion
                WHERE k_id_reporte_servicio = ? AND b_estado_activo
                """,
                Integer.class, reporte);
    }

    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("El informe congelado (V37)")
    class ElInformeCongelado {

        @Autowired private DocumentImageStoreAdapter logos;

        private static final UUID VERIFICACION = UUID.randomUUID();
        private static final UUID PUNTO = UUID.randomUUID();

        private ReportDocument completo() {
            return new ReportDocument(ReportDocument.VERSION,
                    new ReportDocument.Orden("4F2A9C", "CORRECTIVO", true, "TRIMESTRAL", LocalDate.of(2026, 10, 1)),
                    new ReportDocument.Cliente("Clínica del Norte", "900.111.222-3", "Sede Norte", "Calle 1",
                            "Ciudad", "Urgencias", List.of("Ana", "Luis"), List.of("601 1"), List.of()),
                    new ReportDocument.Equipo("Báscula", "Kern", "MS-200", "SN-1", "HV-CDN-0001", null, "BAS-3",
                            "Electrónica", "Pesar", "IIA", "AC", 110, 20, new BigDecimal("0.50"), 60),
                    List.of(new ReportDocument.Verificacion(VERIFICACION, "Masa", "kg", 3,
                            List.of(new ReportDocument.Punto(PUNTO, new BigDecimal("10.5000"))))),
                    new ReportDocument.Empresa("Ejemplo S.A.S.", null, null, null, null, "Garantía", null, "FT-2",
                            "a".repeat(64)),
                    "Ana Pérez",
                    "b".repeat(64));
        }

        private ServiceReport cerrado(ReportDocument documento) {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordIntake(new IntakeCheck(IntakeState.CONFORME, null),
                    new IntakeCheck(IntakeState.CONFORME, null), List.of());
            reporte.finish(documento);
            return reporte;
        }

        @Test
        @DisplayName("el documento vuelve igual, con sus decimales, sus fechas y sus listas en orden")
        void idaYVuelta() {
            ServiceReport reporte = cerrado(completo());
            adapter.save(reporte);

            ServiceReport recuperado = adapter.findById(reporte.getId()).orElseThrow();

            assertThat(recuperado.getDocumento()).isEqualTo(completo());
            assertThat(recuperado.isDocumentoTardio()).isFalse();
        }

        @Test
        @DisplayName("solo los cerrados sin documento quedan por congelar")
        void porCongelar() {
            ServiceReport conDocumento = cerrado(completo());
            adapter.save(conDocumento);
            ServiceReport viejo = cerrado(completo());
            adapter.save(viejo);
            jdbcTemplate.update("UPDATE reporte_servicio SET t_documento = NULL WHERE k_id_reporte_servicio = ?",
                    viejo.getId());

            assertThat(adapter.findFinishedWithoutDocument()).extracting(ServiceReport::getId)
                    .containsExactly(viejo.getId());
        }

        @Test
        @DisplayName("uno congelado tarde vuelve con su marca: que se sepa que no es la copia del día del servicio")
        void tardio() {
            ServiceReport viejo = cerrado(completo());
            adapter.save(viejo);
            jdbcTemplate.update("UPDATE reporte_servicio SET t_documento = NULL WHERE k_id_reporte_servicio = ?",
                    viejo.getId());
            ServiceReport porCongelar = adapter.findById(viejo.getId()).orElseThrow();

            porCongelar.freezeLate(completo());
            adapter.save(porCongelar);

            ServiceReport recuperado = adapter.findById(viejo.getId()).orElseThrow();
            assertThat(recuperado.isDocumentoTardio()).isTrue();
            assertThat(recuperado.getDocumento()).isEqualTo(completo());
        }

        @Test
        @DisplayName("la base impide un borrador con documento, o uno tardío sin él")
        void laBaseLoImpide() {
            Alcance alcance = unAlcance();
            ServiceReport borrador = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(borrador);

            assertThatThrownBy(() -> jdbcTemplate.update(
                            "UPDATE reporte_servicio SET t_documento = '{}'::jsonb WHERE k_id_reporte_servicio = ?",
                            borrador.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            ServiceReport cerrado = cerrado(completo());
            adapter.save(cerrado);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE reporte_servicio SET t_documento = NULL,"
                            + " b_documento_tardio = true WHERE k_id_reporte_servicio = ?", cerrado.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("cada versión del logo se guarda una vez, y se recupera por su huella")
        void elLogo() {
            DocumentImage logo =
                    new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 9}, "image/png");

            logos.store(logo);
            logos.store(new DocumentImage(logo.contenido(), "image/png"));

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM imagen_documento", Integer.class))
                    .isEqualTo(1);
            assertThat(logos.find(logo.huella())).contains(logo);
            assertThat(logos.find("b".repeat(64))).isEmpty();
        }
    }

    @Nested
    @DisplayName("La firma de quien recibe (V39)")
    class LaFirmaDeQuienRecibe {

        @Autowired private DocumentImageStoreAdapter imagenes;

        private static final DocumentImage FIRMA = DocumentImage.firma(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 11});
        private static final Handover ANA = new Handover("Ana Pérez", "52123456", "Jefe de enfermeria");

        private ServiceReport cerrado() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordIntake(new IntakeCheck(IntakeState.CONFORME, null),
                    new IntakeCheck(IntakeState.CONFORME, null), List.of());
            reporte.finish(UnDocumento.CUALQUIERA);
            return reporte;
        }

        @Test
        @DisplayName("firmado después de cerrar, vuelve con quién firmó, su huella y cuándo")
        void idaYVuelta() {
            ServiceReport reporte = cerrado();
            adapter.save(reporte);
            ServiceReport aFirmar = adapter.findById(reporte.getId()).orElseThrow();
            imagenes.store(FIRMA);
            aFirmar.signReceipt(ANA, FIRMA.huella(), java.time.LocalDateTime.of(2026, 10, 17, 9, 30, 15));
            adapter.save(aFirmar);

            ServiceReport recuperado = adapter.findById(reporte.getId()).orElseThrow();

            assertThat(recuperado.getFirmaRecibe()).isEqualTo(new ReceiptSignature(FIRMA.huella(),
                    java.time.LocalDateTime.of(2026, 10, 17, 9, 30, 15)));
            assertThat(recuperado.getRecibe()).isEqualTo(ANA);
            assertThat(imagenes.find(recuperado.getFirmaRecibe().huella())).contains(FIRMA);
        }

        @Test
        @DisplayName("sin firmar vuelve sin firma")
        void sinFirmar() {
            ServiceReport reporte = cerrado();
            adapter.save(reporte);

            assertThat(adapter.findById(reporte.getId()).orElseThrow().getFirmaRecibe()).isNull();
        }

        @Test
        @DisplayName("la base impide una firma sin imagen guardada, sin fecha o sin nombre")
        void laBaseLoImpide() {
            ServiceReport reporte = cerrado();
            adapter.save(reporte);
            imagenes.store(FIRMA);

            // Una huella que no está en imagen_documento.
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE reporte_servicio SET n_recibe_nombre = 'Ana',"
                            + " k_huella_firma_recibe = ?, f_firma_recibe = now() WHERE k_id_reporte_servicio = ?",
                            "c".repeat(64), reporte.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE reporte_servicio SET n_recibe_nombre = 'Ana',"
                            + " k_huella_firma_recibe = ? WHERE k_id_reporte_servicio = ?",
                            FIRMA.huella(), reporte.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE reporte_servicio SET k_huella_firma_recibe = ?,"
                            + " f_firma_recibe = now() WHERE k_id_reporte_servicio = ?",
                            FIRMA.huella(), reporte.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // Una fecha sin firma tampoco.
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE reporte_servicio SET f_firma_recibe = now()"
                            + " WHERE k_id_reporte_servicio = ?", reporte.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // Y con todo, entra: lo de arriba falla por lo que dice y no por otra cosa.
            assertThat(jdbcTemplate.update("UPDATE reporte_servicio SET n_recibe_nombre = 'Ana',"
                            + " k_huella_firma_recibe = ?, f_firma_recibe = now() WHERE k_id_reporte_servicio = ?",
                    FIRMA.huella(), reporte.getId())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Al guardar y recuperar")
    class IdaYVuelta {

        @Test
        @DisplayName("el reporte vuelve con sus siete campos, el estado y la fecha de cierre")
        void idaYVuelta() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.fill("No enciende", "Fuente quemada", "Cambio de fuente", "Queda en prueba",
                    "Sobretension", "Fuente 12 V", ServiceResult.OPERATIVO_CON_RESTRICCIONES);
            reporte.recordIntake(new IntakeCheck(IntakeState.CONFORME, null),
                    new IntakeCheck(IntakeState.CONFORME, null), List.of());
            reporte.finish(UnDocumento.CUALQUIERA);
            adapter.save(reporte);

            ServiceReport recuperado = adapter.findById(reporte.getId()).orElseThrow();

            assertThat(recuperado.getIdOrdenTrabajo()).isEqualTo(alcance.orden());
            assertThat(recuperado.getIdEquipoCliente()).isEqualTo(alcance.equipo());
            assertThat(recuperado.getEstado()).isEqualTo(ReportState.FINALIZADO);
            assertThat(recuperado.getFallaReportada()).isEqualTo("No enciende");
            assertThat(recuperado.getDiagnostico()).isEqualTo("Fuente quemada");
            assertThat(recuperado.getProcedimientos()).isEqualTo("Cambio de fuente");
            assertThat(recuperado.getObservaciones()).isEqualTo("Queda en prueba");
            // La causa y los repuestos del correctivo, de V24.
            assertThat(recuperado.getCausa()).isEqualTo("Sobretension");
            assertThat(recuperado.getRepuestos()).isEqualTo("Fuente 12 V");
            assertThat(recuperado.getResultado())
                    .isEqualTo(ServiceResult.OPERATIVO_CON_RESTRICCIONES);
            assertThat(recuperado.getFinalizado()).isNotNull();
            assertThat(recuperado.isEstadoActivo()).isTrue();
        }

        @Test
        @DisplayName("un borrador vacio vuelve vacio, sin inventarse nada")
        void unBorradorVacio() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(reporte);

            ServiceReport recuperado = adapter.findById(reporte.getId()).orElseThrow();

            assertThat(recuperado.getEstado()).isEqualTo(ReportState.BORRADOR);
            assertThat(recuperado.getResultado()).isNull();
            assertThat(recuperado.getFinalizado()).isNull();
            assertThat(recuperado.getFallaReportada()).isNull();
            assertThat(recuperado.lecturasActivas()).isEmpty();
        }

        @Test
        @DisplayName("rehidratar no resucita eventos: lo que se leyo de la base no ocurrio ahora")
        void rehidratarNoEmite() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(reporte);

            assertThat(adapter.findById(reporte.getId()).orElseThrow().pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("las lecturas vuelven con su punto, su numero, sus dos valores y su unidad")
        void lasLecturasVuelvenCompletas() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.recordVerification(List.of(
                    VerificationReading.of(alcance.verificacion(), alcance.punto(), 1, new BigDecimal("50"),
                            new BigDecimal("50.2"), "mmHg"),
                    VerificationReading.of(alcance.verificacion(), alcance.punto(), 2, new BigDecimal("50"),
                            new BigDecimal("49.8"), "mmHg")));
            adapter.save(reporte);

            ServiceReport recuperado = adapter.findById(reporte.getId()).orElseThrow();

            assertThat(recuperado.lecturasActivas()).hasSize(2);
            assertThat(recuperado.lecturasActivas())
                    .extracting(VerificationReading::idPuntoVerificacion)
                    .containsOnly(alcance.punto());
            assertThat(recuperado.lecturasActivas())
                    .extracting(VerificationReading::valorEquipo)
                    .containsExactlyInAnyOrder(
                            new BigDecimal("50.2000"), new BigDecimal("49.8000"));
            assertThat(recuperado.lecturasActivas())
                    .extracting(VerificationReading::unidad).containsOnly("mmHg");
        }

        @Test
        @DisplayName("una lectura sin punto vuelve sin punto: es la modalidad variable")
        void unaLecturaSinPunto() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.recordVerification(List.of(VerificationReading.of(
                    alcance.verificacion(), null, 1,
                    new BigDecimal("1"), new BigDecimal("1.1"), "mmHg")));
            adapter.save(reporte);

            assertThat(adapter.findById(reporte.getId()).orElseThrow()
                            .lecturasActivas().getFirst().idPuntoVerificacion())
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Los patrones de cada verificacion (V30) y la tolerancia (V27)")
    class ElPatron {

        private UUID unPatronEnElCatalogo() {
            UUID patron = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO equipo_patron (k_id_equipo_patron, n_nombre, n_marca, k_serie, n_certificado,"
                            + " f_vencimiento_certificado)"
                            + " VALUES (?, 'Juego de pesas', 'Kern', ?, 'CAL-2026-0412', '2027-04-30')",
                    patron, "KF1-" + unico());

            return patron;
        }

        private long filasDePatron(UUID reporte, boolean activas) {
            return jdbcTemplate.queryForObject("SELECT count(*) FROM patron_verificacion_reporte"
                    + " WHERE k_id_reporte_servicio = ? AND b_estado_activo = ?", Long.class, reporte, activas);
        }

        @Test
        @DisplayName("los patrones vuelven con lo que se copio, ficha incluida, aunque el catalogo cambie despues")
        void vuelvenCopiados() {
            Alcance alcance = unAlcance();
            UUID pesas = unPatronEnElCatalogo();
            UUID termometro = unPatronEnElCatalogo();
            UsedReferenceStandard conFicha = new UsedReferenceStandard(pesas, "Juego de pesas", "Kern", "KF1-1",
                    "CAL-2026-0412", LocalDate.of(2027, 4, 30), "MS-F1", "BB-PAT-003", "1 mg a 2 kg", "0,1 mg",
                    LocalDate.of(2026, 4, 30), "Metrologia Andina");
            // Sin marca y sin ficha: lo de un patron hecho en casa, o copiado antes de V30.
            UsedReferenceStandard sinFicha = new UsedReferenceStandard(termometro, "Termometro", null, "T-9",
                    "CAL-9", LocalDate.of(2027, 1, 1));
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.useReferenceStandard(alcance.verificacion(), conFicha);
            reporte.useReferenceStandard(alcance.verificacion(), sinFicha);
            adapter.save(reporte);

            // Se renueva el certificado en el catalogo: el reporte no se entera, y no debe.
            jdbcTemplate.update("UPDATE equipo_patron SET n_certificado = 'CAL-2027-0001',"
                    + " f_vencimiento_certificado = '2028-04-30' WHERE k_id_equipo_patron = ?", pesas);

            assertThat(adapter.findById(reporte.getId()).orElseThrow().patronesActivos())
                    .extracting(VerificationStandard::idVerificacion, VerificationStandard::patron)
                    .containsExactlyInAnyOrder(
                            tuple(alcance.verificacion(), conFicha), tuple(alcance.verificacion(), sinFicha));
        }

        @Test
        @DisplayName("sin patrones vuelve sin patrones")
        void sinPatrones() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(reporte);

            assertThat(adapter.findById(reporte.getId()).orElseThrow().getPatrones()).isEmpty();
        }

        @Test
        @DisplayName("quitar uno retira su fila sin borrarla, y volver a ponerlo reactiva la misma")
        void quitarYVolver() {
            Alcance alcance = unAlcance();
            UsedReferenceStandard patron = new UsedReferenceStandard(unPatronEnElCatalogo(), "Juego de pesas",
                    "Kern", "KF1-1", "CAL-1", LocalDate.of(2027, 4, 30));
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.useReferenceStandard(alcance.verificacion(), patron);
            adapter.save(reporte);

            ServiceReport leido = adapter.findById(reporte.getId()).orElseThrow();
            leido.removeReferenceStandard(alcance.verificacion(), patron.id());
            adapter.save(leido);
            assertThat(filasDePatron(reporte.getId(), false)).isEqualTo(1);
            assertThat(adapter.findById(reporte.getId()).orElseThrow().patronesActivos()).isEmpty();

            ServiceReport otraVez = adapter.findById(reporte.getId()).orElseThrow();
            otraVez.useReferenceStandard(alcance.verificacion(), patron);
            adapter.save(otraVez);
            assertThat(filasDePatron(reporte.getId(), true)).isEqualTo(1);
            assertThat(filasDePatron(reporte.getId(), false)).isZero();
        }

        @Test
        @DisplayName("la base no deja el mismo patron activo dos veces en la misma verificacion")
        void unoPorVerificacion() {
            Alcance alcance = unAlcance();
            UUID idPatron = unPatronEnElCatalogo();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.useReferenceStandard(alcance.verificacion(), new UsedReferenceStandard(idPatron, "Pesas",
                    null, "S", "C", LocalDate.of(2027, 1, 1)));
            adapter.save(reporte);
            String otra = "INSERT INTO patron_verificacion_reporte (k_id_patron_verificacion_reporte,"
                    + " k_id_reporte_servicio, k_id_verificacion, k_id_equipo_patron, n_nombre, k_serie,"
                    + " n_certificado, f_vencimiento, b_estado_activo) VALUES (?, ?, ?, ?, 'Pesas', 'S', 'C',"
                    + " '2027-01-01', ?)";

            assertThatThrownBy(() -> jdbcTemplate.update(
                            otra, UUID.randomUUID(), reporte.getId(), alcance.verificacion(), idPatron, true))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // Retirada si cabe: el indice es parcial, y las retiradas son historia.
            assertThat(jdbcTemplate.update(
                    otra, UUID.randomUUID(), reporte.getId(), alcance.verificacion(), idPatron, false)).isEqualTo(1);
        }

        @Test
        @DisplayName("la tolerancia de cada lectura va y vuelve, y sin ella vuelve nula")
        void laToleranciaVuelve() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.recordVerification(List.of(
                    VerificationReading.of(alcance.verificacion(), alcance.punto(), 1, new BigDecimal("50"),
                            new BigDecimal("50.7"), "mmHg", new BigDecimal("0.5")),
                    VerificationReading.of(alcance.verificacion(), alcance.punto(), 2, new BigDecimal("50"),
                            new BigDecimal("50.1"), "mmHg")));
            adapter.save(reporte);

            List<VerificationReading> recuperadas = adapter.findById(reporte.getId()).orElseThrow()
                    .lecturasActivas();

            VerificationReading primera = recuperadas.stream().filter(l -> l.secuencia() == 1).findFirst()
                    .orElseThrow();
            VerificationReading segunda = recuperadas.stream().filter(l -> l.secuencia() == 2).findFirst()
                    .orElseThrow();
            assertThat(primera.tolerancia()).isEqualByComparingTo("0.5");
            assertThat(primera.conforme()).isFalse();
            assertThat(segunda.tolerancia()).isNull();
        }
    }

    @Nested
    @DisplayName("El ingreso, los accesorios y quien recibe (V31)")
    class LaRecepcion {

        @Test
        @DisplayName("todo va y vuelve, y los accesorios sustituidos quedan retirados en la tabla")
        void idaYVuelta() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.recordIntake(new IntakeCheck(IntakeState.NO_CONFORME, "Carcasa rayada"),
                    new IntakeCheck(IntakeState.NO_APLICA, null),
                    List.of(ReportAccessory.of(1, "Sonda", true, null),
                            ReportAccessory.of(2, "Cable", false, "Pelado")));
            reporte.recordHandover(new Handover("Ana Pérez", "52123456", "Jefe de enfermeria"));
            adapter.save(reporte);

            ServiceReport leido = adapter.findById(reporte.getId()).orElseThrow();
            assertThat(leido.getIngresoFisico()).isEqualTo(new IntakeCheck(IntakeState.NO_CONFORME, "Carcasa rayada"));
            assertThat(leido.getIngresoOperativo()).isEqualTo(new IntakeCheck(IntakeState.NO_APLICA, null));
            assertThat(leido.accesoriosActivos())
                    .extracting(ReportAccessory::orden, ReportAccessory::nombre, ReportAccessory::conforme)
                    .containsExactly(tuple(1, "Sonda", true), tuple(2, "Cable", false));
            assertThat(leido.getRecibe()).isEqualTo(new Handover("Ana Pérez", "52123456", "Jefe de enfermeria"));

            leido.recordIntake(leido.getIngresoFisico(), leido.getIngresoOperativo(),
                    List.of(ReportAccessory.of(1, "Brazalete", true, null)));
            adapter.save(leido);

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM accesorio_reporte"
                    + " WHERE k_id_reporte_servicio = ? AND NOT b_estado_activo", Long.class, reporte.getId()))
                    .isEqualTo(2);
            assertThat(adapter.findById(reporte.getId()).orElseThrow().accesoriosActivos())
                    .extracting(ReportAccessory::nombre).containsExactly("Brazalete");
        }

        @Test
        @DisplayName("sin registrar vuelve todo nulo y sin accesorios")
        void sinRegistrar() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(reporte);

            ServiceReport leido = adapter.findById(reporte.getId()).orElseThrow();
            assertThat(leido.getIngresoFisico()).isNull();
            assertThat(leido.getIngresoOperativo()).isNull();
            assertThat(leido.getAccesorios()).isEmpty();
            assertThat(leido.getRecibe()).isNull();
        }

        @Test
        @DisplayName("la base no deja un no conforme sin observacion, ni una cedula sin nombre")
        void reglasEnLaBase() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(reporte);
            UUID id = reporte.getId();

            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE reporte_servicio SET t_ingreso_fisico ="
                    + " 'NO_CONFORME' WHERE k_id_reporte_servicio = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE reporte_servicio SET t_ingreso_operativo ="
                    + " 'ROTO' WHERE k_id_reporte_servicio = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE reporte_servicio SET k_recibe_cedula ="
                    + " '52123456' WHERE k_id_reporte_servicio = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO accesorio_reporte (k_id_accesorio_reporte,"
                    + " k_id_reporte_servicio, i_orden, n_nombre, b_conforme) VALUES (?, ?, 1, 'Sonda', false)",
                    UUID.randomUUID(), id))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // Conforme con observacion si cabe, y no conforme con ella tambien.
            assertThat(jdbcTemplate.update("UPDATE reporte_servicio SET t_ingreso_fisico = 'CONFORME',"
                    + " t_ingreso_fisico_observacion = 'Limpio' WHERE k_id_reporte_servicio = ?", id)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("El avance por orden (2026-10-16)")
    class ElAvance {

        private ServiceReport cerrado(Alcance alcance) {
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordIntake(new IntakeCheck(IntakeState.CONFORME, null),
                    new IntakeCheck(IntakeState.CONFORME, null), List.of());
            reporte.finish(UnDocumento.CUALQUIERA);

            return reporte;
        }

        @Test
        @DisplayName("cuenta equipos con reporte vivo cerrado o en borrador, y no los retirados")
        void cuentaLoVivo() {
            // A: un cerrado que se retiro y su sustituto cerrado. Es un equipo, no dos.
            Alcance a = unAlcance();
            ServiceReport retirado = cerrado(a);
            retirado.deactivate();
            adapter.save(retirado);
            adapter.save(cerrado(a));
            // B: un borrador.
            Alcance b = unAlcance();
            adapter.save(ServiceReport.open(b.orden(), b.equipo()));
            // C: solo un retirado: para quien lee, la orden no tiene reportes.
            Alcance c = unAlcance();
            ServiceReport soloRetirado = ServiceReport.open(c.orden(), c.equipo());
            soloRetirado.deactivate();
            adapter.save(soloRetirado);

            List<WorkOrderReportProgress> avance = adapter.progressByWorkOrder();

            assertThat(avance).containsExactlyInAnyOrder(
                    new WorkOrderReportProgress(a.orden(), 1, 0),
                    new WorkOrderReportProgress(b.orden(), 0, 1));
        }
    }

    @Nested
    @DisplayName("El numero (V28)")
    class ElNumero {

        @Test
        @DisplayName("un numero ocupado se reconoce, en mayusculas o no, y uno libre no")
        void numeroOcupado() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(reporte);

            assertThat(adapter.numberTaken(reporte.numero())).isTrue();
            assertThat(adapter.numberTaken(reporte.numero().toLowerCase())).isTrue();
            String otro = reporte.numero().startsWith("0") ? "1" + reporte.numero().substring(1)
                    : "0" + reporte.numero().substring(1);
            assertThat(adapter.numberTaken(otro)).isFalse();
        }

        @Test
        @DisplayName("la base no deja dos reportes con el mismo numero, ni aunque uno este retirado")
        void numeroUnico() {
            Alcance alcance = unAlcance();
            ServiceReport primero = ServiceReport.open(alcance.orden(), alcance.equipo());
            primero.deactivate();
            adapter.save(primero);
            // Mismo prefijo de seis caracteres, resto distinto.
            String mismoPrefijo = primero.getId().toString().substring(0, 6)
                    + (primero.getId().toString().charAt(6) == '0' ? "1" : "0")
                    + primero.getId().toString().substring(7);

            assertThatThrownBy(() -> jdbcTemplate.update(
                            "INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo,"
                                    + " k_id_equipo_cliente, t_estado_reporte) VALUES (?, ?, ?, 'BORRADOR')",
                            UUID.fromString(mismoPrefijo), alcance.orden(), alcance.equipo()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Al guardar dos veces")
    class AlGuardarDosVeces {

        @Test
        @DisplayName("no duplica las lecturas: el adaptador lee la fila antes de volcar")
        void noDuplicaLasLecturas() {
            // Sin la lectura previa, el mapper construiria una entidad nueva en cada guardado y
            // Hibernate insertaria las lecturas otra vez. Le paso al adaptador de clientes con sus
            // contactos y al de ordenes con su alcance.
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.recordVerification(List.of(VerificationReading.of(
                    alcance.verificacion(), alcance.punto(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
            adapter.save(reporte);

            adapter.save(reporte);

            assertThat(filasDeLecturas(reporte.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("una lectura corregida deja la anterior inactiva en vez de borrarla")
        void laLecturaCorregidaSeRetira() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.recordVerification(List.of(VerificationReading.of(
                    alcance.verificacion(), alcance.punto(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
            adapter.save(reporte);

            reporte.recordVerification(List.of(VerificationReading.of(
                    alcance.verificacion(), alcance.punto(), 1, new BigDecimal("50"), new BigDecimal("51.9"), "mmHg")));
            adapter.save(reporte);

            // Dos filas: la corregida sigue ahi, retirada. Aqui nada se borra.
            assertThat(filasDeLecturas(reporte.getId())).isEqualTo(2);
            assertThat(filasActivasDeLecturas(reporte.getId())).isEqualTo(1);
            assertThat(adapter.findById(reporte.getId()).orElseThrow()
                            .lecturasActivas().getFirst().valorEquipo())
                    .isEqualByComparingTo(new BigDecimal("51.9"));
        }

        @Test
        @DisplayName("cerrar un borrador guardado actualiza su fila, no crea otra")
        void cerrarActualizaLaFila() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(reporte);

            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordIntake(new IntakeCheck(IntakeState.CONFORME, null),
                    new IntakeCheck(IntakeState.CONFORME, null), List.of());
            reporte.finish(UnDocumento.CUALQUIERA);
            adapter.save(reporte);

            Integer filas = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM reporte_servicio WHERE k_id_orden_trabajo = ?",
                    Integer.class, alcance.orden());
            assertThat(filas).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT t_estado_reporte FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                            String.class, reporte.getId()))
                    .isEqualTo("FINALIZADO");
        }

        @Test
        @DisplayName("retirar el reporte apaga su fila y sus lecturas se quedan")
        void retirarApagaLaFila() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.recordVerification(List.of(VerificationReading.of(
                    alcance.verificacion(), alcance.punto(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
            adapter.save(reporte);

            reporte.deactivate();
            adapter.save(reporte);

            assertThat(jdbcTemplate.queryForObject(
                            "SELECT b_estado_activo FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                            Boolean.class, reporte.getId()))
                    .isFalse();
            assertThat(filasDeLecturas(reporte.getId())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Al consultar")
    class AlConsultar {

        @Test
        @DisplayName("los reportes de una orden salen todos, retirados incluidos")
        void porOrden() {
            Alcance alcance = unAlcance();
            ServiceReport primero = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(primero);
            primero.deactivate();
            adapter.save(primero);
            ServiceReport segundo = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(segundo);

            assertThat(adapter.findByWorkOrder(alcance.orden())).hasSize(2);
        }

        @Test
        @DisplayName("el reporte vivo de un equipo en una orden es uno solo")
        void elVivoEsUnoSolo() {
            Alcance alcance = unAlcance();
            ServiceReport retirado = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(retirado);
            retirado.deactivate();
            adapter.save(retirado);
            ServiceReport vivo = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(vivo);

            assertThat(adapter.findActiveByWorkOrderAndEquipment(alcance.orden(), alcance.equipo()))
                    .get()
                    .extracting(ServiceReport::getId)
                    .isEqualTo(vivo.getId());
        }

        @Test
        @DisplayName("sin reporte vivo la consulta no devuelve nada, y no es un error")
        void sinReporteVivo() {
            Alcance alcance = unAlcance();

            assertThat(adapter.findActiveByWorkOrderAndEquipment(alcance.orden(), alcance.equipo()))
                    .isEmpty();
        }

        @Test
        @DisplayName("el historial de un equipo son sus reportes, que es la base de la hoja de vida")
        void porEquipo() {
            Alcance alcance = unAlcance();
            adapter.save(ServiceReport.open(alcance.orden(), alcance.equipo()));

            assertThat(adapter.findByEquipment(alcance.equipo())).hasSize(1);
            assertThat(adapter.findByEquipment(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Los pasos del protocolo, desde V22")
    class LosPasos {

        /** Un protocolo de verdad en la base: el paso lleva una foránea a él. */
        private UUID unProtocolo(String nombre) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO protocolo (k_id_protocolo, n_nombre, n_clave, t_descripcion) VALUES (?, ?, ?, 'D')",
                    id, nombre, nombre.toLowerCase() + " " + System.nanoTime());

            return id;
        }

        @Test
        @DisplayName("se guardan al abrir, con lo copiado, y vuelven en su orden")
        void idaYVuelta() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo(), List.of(
                    ReportStep.copied(2, unProtocolo("Eléctrico"), "Eléctrico", "Multímetro.", null),
                    ReportStep.copied(1, unProtocolo("Limpieza"), "Limpieza", "Paño.", "Con guantes")));
            adapter.save(reporte);

            assertThat(adapter.findById(reporte.getId()).orElseThrow().getPasos())
                    .extracting(ReportStep::orden, ReportStep::nombre, ReportStep::indicacion, ReportStep::estado)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(1, "Limpieza", "Con guantes", StepState.PENDIENTE),
                            org.assertj.core.groups.Tuple.tuple(2, "Eléctrico", null, StepState.PENDIENTE));
        }

        @Test
        @DisplayName("marcar un paso se guarda sobre la misma fila, sin duplicarla")
        void marcarPersiste() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo(), List.of(
                    ReportStep.copied(1, unProtocolo("Batería"), "Batería", "Revisar.", null)));
            adapter.save(reporte);

            ServiceReport leido = adapter.findById(reporte.getId()).orElseThrow();
            leido.markStep(1, StepState.NO_APLICA, "No tiene batería");
            adapter.save(leido);

            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM paso_reporte WHERE k_id_reporte_servicio = ? AND b_estado_activo",
                            Integer.class, reporte.getId()))
                    .describedAs("una fila, y vigente: desde V23 la tabla tiene borrado logico como todas")
                    .isEqualTo(1);
            assertThat(adapter.findById(reporte.getId()).orElseThrow().getPasos().getFirst())
                    .extracting(ReportStep::estado, ReportStep::nota)
                    .containsExactly(StepState.NO_APLICA, "No tiene batería");
        }

        @Test
        @DisplayName("la base tampoco deja un «no aplica» sin nota, ni dos pasos con el mismo orden")
        void restricciones() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            adapter.save(reporte);
            UUID protocolo = unProtocolo("Limpieza");
            String insertar = "INSERT INTO paso_reporte (k_id_paso_reporte, k_id_reporte_servicio, i_orden,"
                    + " k_id_protocolo, n_nombre, t_descripcion, t_estado_paso, t_nota)"
                    + " VALUES (?, ?, ?, ?, 'Limpieza', 'D', ?, ?)";

            assertThatThrownBy(() -> jdbcTemplate.update(insertar,
                            UUID.randomUUID(), reporte.getId(), 1, protocolo, "NO_APLICA", null))
                    .isInstanceOf(DataIntegrityViolationException.class);

            jdbcTemplate.update(insertar, UUID.randomUUID(), reporte.getId(), 1, protocolo, "HECHO", null);
            assertThatThrownBy(() -> jdbcTemplate.update(insertar,
                            UUID.randomUUID(), reporte.getId(), 1, protocolo, "HECHO", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
