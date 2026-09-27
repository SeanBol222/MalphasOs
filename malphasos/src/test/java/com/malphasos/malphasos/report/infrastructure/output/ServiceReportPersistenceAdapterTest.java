package com.malphasos.malphasos.report.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.infrastructure.output.BrandPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.EquipmentPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.EquipmentTypePersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.ManufacturerPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.ModelPersistenceAdapter;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
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
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo_equipo",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM punto_verificacion",
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
        String letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String iso = "" + letras.charAt((int) (n % 26))
                + letras.charAt((int) ((n / 26) % 26))
                + letras.charAt((int) ((n / 676) % 26));

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

    /** Un tipo verificable en un punto, para tener un punto real al que apuntar. */
    private EquipmentType unTipoVerificable() {
        return equipmentTypeAdapter.save(EquipmentType.create(
                "Tipo " + unico(), "Definicion", "Cuidados", "Electronica", null, null,
                VerificationMode.PATRON_CONSTANTE, 2,
                List.of(VerificationPoint.of(new BigDecimal("50"), "mmHg")), 100_000L));
    }

    private UUID unModeloDe(EquipmentType tipo) {
        Manufacturer fabricante =
                manufacturerAdapter.save(Manufacturer.create("Draeger " + unico(), null));
        Brand marca = brandAdapter.save(Brand.create("Marca " + unico()));
        Equipment asociacion = equipmentAdapter.save(Equipment.create(tipo.getId(), marca.getId()));

        return modelAdapter.save(Model.create(null, fabricante.getId(), asociacion.getId())).getId();
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

        return new Alcance(orden.getId(), equipo, tipo.puntosActivos().getFirst().id());
    }

    /** Lo que hace falta nombrar para abrir un reporte y ponerle lecturas. */
    private record Alcance(UUID orden, UUID equipo, UUID punto) {
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
    @DisplayName("Al guardar y recuperar")
    class IdaYVuelta {

        @Test
        @DisplayName("el reporte vuelve con los cinco campos, el estado y la fecha de cierre")
        void idaYVuelta() {
            Alcance alcance = unAlcance();
            ServiceReport reporte = ServiceReport.open(alcance.orden(), alcance.equipo());
            reporte.fill("No enciende", "Fuente quemada", "Cambio de fuente", "Queda en prueba",
                    ServiceResult.OPERATIVO_CON_RESTRICCIONES);
            reporte.finish();
            adapter.save(reporte);

            ServiceReport recuperado = adapter.findById(reporte.getId()).orElseThrow();

            assertThat(recuperado.getIdOrdenTrabajo()).isEqualTo(alcance.orden());
            assertThat(recuperado.getIdEquipoCliente()).isEqualTo(alcance.equipo());
            assertThat(recuperado.getEstado()).isEqualTo(ReportState.FINALIZADO);
            assertThat(recuperado.getFallaReportada()).isEqualTo("No enciende");
            assertThat(recuperado.getDiagnostico()).isEqualTo("Fuente quemada");
            assertThat(recuperado.getProcedimientos()).isEqualTo("Cambio de fuente");
            assertThat(recuperado.getObservaciones()).isEqualTo("Queda en prueba");
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
                    VerificationReading.of(alcance.punto(), 1, new BigDecimal("50"),
                            new BigDecimal("50.2"), "mmHg"),
                    VerificationReading.of(alcance.punto(), 2, new BigDecimal("50"),
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
                    null, 1, new BigDecimal("1"), new BigDecimal("1.1"), "mA")));
            adapter.save(reporte);

            assertThat(adapter.findById(reporte.getId()).orElseThrow()
                            .lecturasActivas().getFirst().idPuntoVerificacion())
                    .isNull();
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
                    alcance.punto(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
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
                    alcance.punto(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
            adapter.save(reporte);

            reporte.recordVerification(List.of(VerificationReading.of(
                    alcance.punto(), 1, new BigDecimal("50"), new BigDecimal("51.9"), "mmHg")));
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
            reporte.finish();
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
                    alcance.punto(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
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
}
