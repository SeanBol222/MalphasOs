package com.malphasos.malphasos.workorder.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.infrastructure.output.BrandPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.EquipmentPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.EquipmentTypePersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.ManufacturerPersistenceAdapter;
import com.malphasos.malphasos.equipment.infrastructure.output.ModelPersistenceAdapter;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.SelectedEquipment;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDate;
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
 * La persistencia de las órdenes de trabajo contra un PostgreSQL real.
 *
 * <p><b>Por qué existe esta clase.</b> `WorkOrderServiceTest` usa un doble del puerto, de modo que
 * hasta el 2026-09-13 <b>nada ejercía</b> el mapper ni el adaptador. Y ahí no vive un mapeo trivial
 * sino la conciliación del alcance: un equipo retirado <b>deja su fila inactiva en vez de
 * desaparecer</b>, y uno readmitido <b>reactiva la suya con el área nueva</b>. Nada de eso se puede
 * comprobar con dobles, porque lo que hay que mirar es la fila.
 *
 * <p>Las comprobaciones van contra la tabla con SQL directo a propósito. Preguntarle al propio
 * agregado si el equipo está o no lo contestaría el mapper, que es justo la pieza bajo prueba.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM orden_trabajo_equipo",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
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
class WorkOrderPersistenceAdapterTest {

    @Autowired private WorkOrderPersistenceAdapter adapter;
    @Autowired private ManufacturerPersistenceAdapter manufacturerAdapter;
    @Autowired private BrandPersistenceAdapter brandAdapter;
    @Autowired private EquipmentTypePersistenceAdapter equipmentTypeAdapter;
    @Autowired private EquipmentPersistenceAdapter equipmentAdapter;
    @Autowired private ModelPersistenceAdapter modelAdapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------------------
    // Andamiaje: la cadena completa que una orden necesita para existir
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

    private UUID unModelo() {
        Manufacturer fabricante =
                manufacturerAdapter.save(Manufacturer.create("Draeger " + unico(), null));
        Brand marca = brandAdapter.save(Brand.create("Marca " + unico()));
        EquipmentType tipo = equipmentTypeAdapter.save(EquipmentType.create(
                "Tipo " + unico(), "Definicion", "Cuidados", "Electronica", null, null, null, 100_000L));
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

    private UUID unIngeniero() {
        UUID persona = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido,
                                     t_tipo_persona)
                VALUES (?, ?, 'Sean', 'Bolivar', 'ENGINEER')
                """,
                persona, String.valueOf(System.nanoTime() % 1_000_000_000L));

        return persona;
    }

    private WorkOrder unaOrdenDe(UUID cliente, UUID sede) {
        return WorkOrder.schedule(cliente, sede, LocalDate.of(2026, 10, 1),
                Periodicity.TRIMESTRAL, ServiceType.PREVENTIVO);
    }

    /** Las filas del puente de una orden, activas e inactivas, que es lo que la tabla guarda. */
    private Integer filasDelAlcance(UUID orden) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orden_trabajo_equipo WHERE k_id_orden_trabajo = ?",
                Integer.class, orden);
    }

    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Al guardar y recuperar")
    class IdaYVuelta {

        @Test
        @DisplayName("la orden vuelve con todo lo que se le puso, sin perder el estado")
        void idaYVuelta() {
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID area = unAreaDe(sede);
            UUID equipo = unEquipoEn(area, unModelo());
            UUID ingeniero = unIngeniero();

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.addEquipment(equipo, area);
            orden.assignTo(ingeniero);
            orden.start();
            adapter.save(orden);

            WorkOrder recuperada = adapter.findById(orden.getId()).orElseThrow();

            assertThat(recuperada.getIdCliente()).isEqualTo(cliente);
            assertThat(recuperada.getIdSede()).isEqualTo(sede);
            assertThat(recuperada.getFechaMantenimiento()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(recuperada.getPeriodicidad()).isEqualTo(Periodicity.TRIMESTRAL);
            assertThat(recuperada.getTipoServicio()).isEqualTo(ServiceType.PREVENTIVO);
            assertThat(recuperada.getEstadoEjecucion()).isEqualTo(ExecutionState.EN_EJECUCION);
            assertThat(recuperada.getIdIngeniero()).isEqualTo(ingeniero);
            assertThat(recuperada.isEstadoActivo()).isTrue();
            assertThat(recuperada.getEquipos()).singleElement()
                    .extracting(SelectedEquipment::getIdEquipoCliente,
                            SelectedEquipment::getIdAreaServicio)
                    .containsExactly(equipo, area);
        }

        @Test
        @DisplayName("rehidratar no resucita eventos: lo que se leyó de la base no ocurrió ahora")
        void rehidratarNoEmite() {
            UUID cliente = unCliente();
            WorkOrder orden = unaOrdenDe(cliente, unaSedeDe(cliente));
            adapter.save(orden);

            assertThat(adapter.findById(orden.getId()).orElseThrow().pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("guardar dos veces no duplica el alcance")
        void guardarDosVecesNoDuplica() {
            // El adaptador lee la fila existente antes de guardar. Sin esa lectura el mapper
            // construiria una entidad nueva cada vez y Hibernate insertaria el alcance de nuevo.
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID area = unAreaDe(sede);
            UUID equipo = unEquipoEn(area, unModelo());

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.addEquipment(equipo, area);
            adapter.save(orden);
            adapter.save(adapter.findById(orden.getId()).orElseThrow());

            assertThat(filasDelAlcance(orden.getId())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Al mover un equipo dentro y fuera del alcance")
    class ConciliacionDelAlcance {

        @Test
        @DisplayName("retirarlo deja la fila inactiva, no la borra")
        void retirarNoBorra() {
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID area = unAreaDe(sede);
            UUID equipo = unEquipoEn(area, unModelo());

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.addEquipment(equipo, area);
            adapter.save(orden);

            orden.removeEquipment(equipo);
            adapter.save(orden);

            // La fila sigue ahi: el historial de un equipo -en que ordenes se le intervino- dejaria
            // de ser cierto si se borrara, y la llave compuesta impediria reinsertarla.
            assertThat(filasDelAlcance(orden.getId())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            """
                            SELECT b_estado_activo FROM orden_trabajo_equipo
                            WHERE k_id_orden_trabajo = ? AND k_id_equipo_cliente = ?
                            """,
                            Boolean.class, orden.getId(), equipo))
                    .isFalse();
        }

        @Test
        @DisplayName("el agregado no vuelve a cargar lo que salió del alcance")
        void loRetiradoNoVuelveEnElAgregado() {
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID area = unAreaDe(sede);
            UUID equipo = unEquipoEn(area, unModelo());

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.addEquipment(equipo, area);
            adapter.save(orden);
            orden.removeEquipment(equipo);
            adapter.save(orden);

            // La fila esta, pero el agregado responde "sobre que se trabaja en esta orden".
            assertThat(adapter.findById(orden.getId()).orElseThrow().getEquipos()).isEmpty();
        }

        @Test
        @DisplayName("readmitirlo reactiva su fila con el area nueva, sin crear otra")
        void readmitirloReactivaConElAreaNueva() {
            // Es la unica forma de corregir un area congelada, y por eso retirar y volver a anadir
            // significa exactamente eso. Lo que no puede pasar es que aparezcan dos filas: la llave
            // compuesta lo impediria y el guardado fallaria.
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID areaVieja = unAreaDe(sede);
            UUID areaNueva = unAreaDe(sede);
            UUID equipo = unEquipoEn(areaVieja, unModelo());

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.addEquipment(equipo, areaVieja);
            adapter.save(orden);

            orden.removeEquipment(equipo);
            adapter.save(orden);

            orden.addEquipment(equipo, areaNueva);
            WorkOrder guardada = adapter.save(orden);

            assertThat(filasDelAlcance(orden.getId())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            """
                            SELECT k_id_area_servicio FROM orden_trabajo_equipo
                            WHERE k_id_orden_trabajo = ? AND k_id_equipo_cliente = ?
                            """,
                            UUID.class, orden.getId(), equipo))
                    .isEqualTo(areaNueva);
            assertThat(guardada.getEquipos()).singleElement()
                    .extracting(SelectedEquipment::getIdAreaServicio)
                    .isEqualTo(areaNueva);
        }

        @Test
        @DisplayName("un traslado del equipo no reescribe el area que la orden congeló")
        void trasladarElEquipoNoTocaElAlcanceGuardado() {
            // El puente no lleva clave foranea compuesta contra equipo_cliente justamente para que
            // esto sea posible; aqui se comprueba desde el otro lado, el de la orden.
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID areaOriginal = unAreaDe(sede);
            UUID otraArea = unAreaDe(sede);
            UUID equipo = unEquipoEn(areaOriginal, unModelo());

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.addEquipment(equipo, areaOriginal);
            adapter.save(orden);

            jdbcTemplate.update(
                    "UPDATE equipo_cliente SET k_id_area_servicio = ? WHERE k_id_equipo_cliente = ?",
                    otraArea, equipo);

            assertThat(adapter.findById(orden.getId()).orElseThrow().getEquipos())
                    .singleElement()
                    .extracting(SelectedEquipment::getIdAreaServicio)
                    .isEqualTo(areaOriginal);
        }
    }

    @Nested
    @DisplayName("Al consultar")
    class Consultas {

        @Test
        @DisplayName("buscar por equipo no devuelve la orden de la que ese equipo salió")
        void buscarPorEquipoIgnoraLoRetirado() {
            // Es lo que justifica el @Query escrito a mano: la derivacion por nombre no puede
            // expresar la condicion sobre la fila intermedia.
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID area = unAreaDe(sede);
            UUID equipo = unEquipoEn(area, unModelo());

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.addEquipment(equipo, area);
            adapter.save(orden);

            assertThat(adapter.findByEquipment(equipo)).extracting(WorkOrder::getId)
                    .containsExactly(orden.getId());

            orden.removeEquipment(equipo);
            adapter.save(orden);

            assertThat(adapter.findByEquipment(equipo)).isEmpty();
        }

        @Test
        @DisplayName("por cliente, por sede y por ingeniero se devuelve la orden que corresponde")
        void losTresFiltrosEncuentranLoSuyo() {
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID otraSede = unaSedeDe(cliente);
            UUID ingeniero = unIngeniero();

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.assignTo(ingeniero);
            adapter.save(orden);
            adapter.save(unaOrdenDe(cliente, otraSede));

            assertThat(adapter.findByClient(cliente)).hasSize(2);
            assertThat(adapter.findByHeadquarter(sede)).extracting(WorkOrder::getId)
                    .containsExactly(orden.getId());
            assertThat(adapter.findByEngineer(ingeniero)).extracting(WorkOrder::getId)
                    .containsExactly(orden.getId());
        }

        @Test
        @DisplayName("una orden cancelada se sigue guardando y recuperando: no se borra nada")
        void laCanceladaSigueAhi() {
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);

            WorkOrder orden = unaOrdenDe(cliente, sede);
            adapter.save(orden);
            orden.cancel();
            adapter.save(orden);

            assertThat(adapter.findById(orden.getId()).orElseThrow().isEstadoActivo()).isFalse();
        }
    }
}
