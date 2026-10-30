package com.malphasos.malphasos.workorder.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDate;
import java.util.Set;
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
 * hasta el 2026-09-13 <b>nada ejercía</b> el mapper ni el adaptador. Su parte más delicada era la
 * conciliación del alcance con la tabla puente, que {@code V50} quitó: desde entonces los equipos de una
 * orden son sus reportes, y lo que queda aquí es la ida y vuelta de la orden y sus consultas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
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
class WorkOrderPersistenceAdapterTest {

    @Autowired private WorkOrderPersistenceAdapter adapter;
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

    private UUID guardadaSinGarantia(UUID cliente, UUID sede) {
        WorkOrder orden = unaOrdenDe(cliente, sede);
        adapter.save(orden);

        return orden.getId();
    }

    private WorkOrder unaOrdenDe(UUID cliente, UUID sede) {
        return WorkOrder.schedule(cliente, sede, LocalDate.of(2026, 10, 1),
                Periodicity.TRIMESTRAL, ServiceType.PREVENTIVO);
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
            UUID ingeniero = unIngeniero();

            WorkOrder orden = unaOrdenDe(cliente, sede);
            orden.assignTo(ingeniero);
            orden.start(1);
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
        }

        @Test
        @DisplayName("la garantia vuelve como se guardo, y el numero es el del identificador")
        void garantiaYNumero() {
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            WorkOrder orden = WorkOrder.schedule(cliente, sede, LocalDate.of(2026, 10, 1),
                    Periodicity.MENSUAL, ServiceType.CORRECTIVO, true);
            adapter.save(orden);

            WorkOrder recuperada = adapter.findById(orden.getId()).orElseThrow();

            assertThat(recuperada.isGarantia()).isTrue();
            assertThat(recuperada.numero()).isEqualTo(orden.numero());
            assertThat(adapter.findById(guardadaSinGarantia(cliente, sede)).orElseThrow().isGarantia()).isFalse();
        }

        @Test
        @DisplayName("numberTaken dice si el prefijo ya lo tiene una orden, sin importar mayusculas")
        void numeroTomado() {
            UUID cliente = unCliente();
            WorkOrder orden = unaOrdenDe(cliente, unaSedeDe(cliente));
            adapter.save(orden);

            assertThat(adapter.numberTaken(orden.numero())).isTrue();
            assertThat(adapter.numberTaken(orden.numero().toLowerCase())).isTrue();
            // Un identificador es hexadecimal: un prefijo con Z no lo puede tener ninguna orden.
            assertThat(adapter.numberTaken("ZZZZZZ")).isFalse();
        }

        @Test
        @DisplayName("rehidratar no resucita eventos: lo que se leyó de la base no ocurrió ahora")
        void rehidratarNoEmite() {
            UUID cliente = unCliente();
            WorkOrder orden = unaOrdenDe(cliente, unaSedeDe(cliente));
            adapter.save(orden);

            assertThat(adapter.findById(orden.getId()).orElseThrow().pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Al consultar")
    class Consultas {

        @Test
        @DisplayName("varias por su identificador: las que existen, y ninguna con la lista vacia")
        void variasPorId() {
            // Desde V50 es lo que contesta en que ordenes esta un equipo: cuales son lo dicen sus reportes.
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID una = guardadaSinGarantia(cliente, sede);
            UUID otra = guardadaSinGarantia(cliente, sede);
            guardadaSinGarantia(cliente, sede);

            assertThat(adapter.findAllById(java.util.List.of(una, otra, UUID.randomUUID())))
                    .extracting(WorkOrder::getId).containsExactlyInAnyOrder(una, otra);
            assertThat(adapter.findAllById(java.util.List.of())).isEmpty();
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

    @Nested
    @DisplayName("La agenda: las ordenes de un periodo")
    class Agenda {

        private WorkOrder ordenDel(UUID cliente, UUID sede, LocalDate fecha) {
            WorkOrder orden = WorkOrder.schedule(cliente, sede, fecha, Periodicity.TRIMESTRAL, ServiceType.PREVENTIVO);
            adapter.save(orden);

            return orden;
        }

        @Test
        @DisplayName("los dos extremos van incluidos, lo de fuera no entra, y sale ordenado por fecha")
        void extremosIncluidosYOrdenadas() {
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);

            WorkOrder ultimo = ordenDel(cliente, sede, LocalDate.of(2026, 10, 31));
            WorkOrder primero = ordenDel(cliente, sede, LocalDate.of(2026, 10, 1));
            WorkOrder enMedio = ordenDel(cliente, sede, LocalDate.of(2026, 10, 15));
            ordenDel(cliente, sede, LocalDate.of(2026, 9, 30));
            ordenDel(cliente, sede, LocalDate.of(2026, 11, 1));

            // Se guardaron desordenadas a proposito: el orden tiene que venir de la consulta.
            assertThat(adapter.findInPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null))
                    .extracting(WorkOrder::getId)
                    .containsExactly(primero.getId(), enMedio.getId(), ultimo.getId());
        }

        @Test
        @DisplayName("con ingeniero, solo las suyas")
        void soloLasDelIngeniero() {
            UUID cliente = unCliente();
            UUID sede = unaSedeDe(cliente);
            UUID ingeniero = unIngeniero();

            WorkOrder suya = ordenDel(cliente, sede, LocalDate.of(2026, 10, 5));
            suya.assignTo(ingeniero);
            adapter.save(suya);
            ordenDel(cliente, sede, LocalDate.of(2026, 10, 6));

            assertThat(adapter.findInPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), ingeniero, null))
                    .extracting(WorkOrder::getId)
                    .containsExactly(suya.getId());
        }

        @Test
        @DisplayName("acotada a unos clientes, solo las de esos; con la lista vacia, ninguna")
        void acotadaAClientes() {
            UUID mio = unCliente();
            UUID ajeno = unCliente();
            WorkOrder propia = ordenDel(mio, unaSedeDe(mio), LocalDate.of(2026, 10, 5));
            ordenDel(ajeno, unaSedeDe(ajeno), LocalDate.of(2026, 10, 5));

            LocalDate desde = LocalDate.of(2026, 10, 1);
            LocalDate hasta = LocalDate.of(2026, 10, 31);

            assertThat(adapter.findInPeriod(desde, hasta, null, Set.of(mio)))
                    .extracting(WorkOrder::getId).containsExactly(propia.getId());
            assertThat(adapter.findInPeriod(desde, hasta, null, Set.of())).isEmpty();
        }

        @Test
        @DisplayName("ingeniero y clientes a la vez: las del ingeniero en esos clientes")
        void ingenieroYClientes() {
            UUID mio = unCliente();
            UUID ajeno = unCliente();
            UUID ingeniero = unIngeniero();

            WorkOrder enMio = ordenDel(mio, unaSedeDe(mio), LocalDate.of(2026, 10, 5));
            enMio.assignTo(ingeniero);
            adapter.save(enMio);
            WorkOrder enAjeno = ordenDel(ajeno, unaSedeDe(ajeno), LocalDate.of(2026, 10, 5));
            enAjeno.assignTo(ingeniero);
            adapter.save(enAjeno);

            assertThat(adapter.findInPeriod(
                            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), ingeniero, Set.of(mio)))
                    .extracting(WorkOrder::getId)
                    .containsExactly(enMio.getId());
        }
    }
}
