package com.malphasos.malphasos.client.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.domain.manager.ManagerAssignment;
import com.malphasos.malphasos.client.domain.manager.ManagerType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Ejercita el adaptador contra un PostgreSQL real.
 *
 * <p>Desde `V41` (2026-10-27) lo que importa es que un encargado se lea <b>entero</b> desde sus dos
 * tablas —todas sus sedes y todas sus áreas— y que guardar no borre ni duplique: relevar apaga la fila,
 * y volver a asignar la enciende. Hasta ese día era que la asignación única del agregado cupiera en el
 * {@code CHECK} que ataba el tipo a una de dos columnas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM encargado_sede",
            "DELETE FROM encargado_area",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente",
            "DELETE FROM ciudad",
            "DELETE FROM pais",
            "DELETE FROM persona"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ManagerPersistenceAdapterTest {

    @Autowired private ManagerPersistenceAdapter adapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Diez digitos exactos, con ceros a la izquierda si hacen falta.
     *
     * <p>El formato fijo no es cosmetico: {@code k_cedula} es {@code varchar(10)} y
     * {@code k_documento} {@code varchar(11)}, asi que el valor se recortaba a diez caracteres. Un
     * resto de {@code nanoTime()} por debajo de mil millones tiene nueve digitos, y recortarlo
     * lanzaba {@code StringIndexOutOfBoundsException} en una de cada cien ejecuciones. Rellenando a
     * la izquierda la longitud deja de depender del reloj.
     */
    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private UUID unaPersona() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido,
                                     t_tipo_persona)
                VALUES (?, ?, 'Grace', 'Hopper', 'MANAGER')
                """,
                id, unico());

        return id;
    }

    private UUID unaSede() {
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
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
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', 'Hospital')
                """,
                cliente, unico());
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero,
                                  k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30-40', ?, ?)
                """,
                sede, "Sede " + n, cliente, ciudad);

        return sede;
    }

    private UUID unArea(UUID sede) {
        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, "Area " + System.nanoTime(), sede);

        return area;
    }

    private boolean activa(String tabla, String columna, UUID persona, UUID destino) {
        return jdbcTemplate.queryForObject(
                "SELECT b_estado_activo FROM " + tabla + " WHERE k_identificador = ? AND " + columna + " = ?",
                Boolean.class, persona, destino);
    }

    @Test
    @DisplayName("un encargado de sede se guarda en encargado_sede y se lee con su sede")
    void encargadoDeSede() {
        UUID persona = unaPersona();
        UUID sede = unaSede();

        adapter.save(Manager.forHeadquarter(persona, sede));

        Manager recuperado = adapter.findByPerson(persona).orElseThrow();
        assertThat(recuperado.getIdsSede()).containsExactly(sede);
        assertThat(recuperado.getIdsAreaServicio()).isEmpty();
        assertThat(activa("encargado_sede", "k_id_sede", persona, sede)).isTrue();
    }

    @Test
    @DisplayName("un encargado de area se guarda en encargado_area")
    void encargadoDeArea() {
        UUID persona = unaPersona();
        UUID area = unArea(unaSede());

        adapter.save(Manager.forServiceArea(persona, area));

        Manager recuperado = adapter.findByPerson(persona).orElseThrow();
        assertThat(recuperado.getIdsAreaServicio()).containsExactly(area);
        assertThat(recuperado.getIdsSede()).isEmpty();
    }

    @Test
    @DisplayName("se lee entero: todas sus sedes y todas sus areas, de las dos tablas")
    void variasAsignaciones() {
        UUID persona = unaPersona();
        UUID norte = unaSede();
        UUID sur = unaSede();
        UUID uci = unArea(norte);

        Manager encargado = Manager.forHeadquarter(persona, norte);
        encargado.assignTo(ManagerType.HEADQUARTER, sur);
        encargado.assignTo(ManagerType.SERVICE_AREA, uci);
        adapter.save(encargado);

        Manager recuperado = adapter.findByPerson(persona).orElseThrow();
        assertThat(recuperado.getIdsSede()).containsExactlyInAnyOrder(norte, sur);
        assertThat(recuperado.getIdsAreaServicio()).containsExactly(uci);
    }

    @Test
    @DisplayName("una persona sin asignaciones no es encargada")
    void sinAsignaciones() {
        assertThat(adapter.findByPerson(unaPersona())).isEmpty();
    }

    @Test
    @DisplayName("relevar de una conserva la fila apagada y deja las demas como estaban")
    void relevarDeUna() {
        UUID persona = unaPersona();
        UUID norte = unaSede();
        UUID sur = unaSede();
        Manager encargado = Manager.forHeadquarter(persona, norte);
        encargado.assignTo(ManagerType.HEADQUARTER, sur);
        adapter.save(encargado);

        Manager leido = adapter.findByPerson(persona).orElseThrow();
        leido.relieveFrom(ManagerType.HEADQUARTER, norte);
        adapter.save(leido);

        assertThat(activa("encargado_sede", "k_id_sede", persona, norte)).isFalse();
        assertThat(activa("encargado_sede", "k_id_sede", persona, sur)).isTrue();
        assertThat(adapter.findByPerson(persona).orElseThrow().getAsignaciones())
                .containsExactlyInAnyOrder(
                        new ManagerAssignment(ManagerType.HEADQUARTER, norte, false),
                        new ManagerAssignment(ManagerType.HEADQUARTER, sur, true));
    }

    @Test
    @DisplayName("volver a asignar reactiva la misma fila: la llave no deja duplicarla")
    void reasignarReactiva() {
        UUID persona = unaPersona();
        UUID sede = unaSede();
        adapter.save(Manager.forHeadquarter(persona, sede));
        Manager relevado = adapter.findByPerson(persona).orElseThrow();
        relevado.deactivate();
        adapter.save(relevado);

        Manager otraVez = adapter.findByPerson(persona).orElseThrow();
        otraVez.assignTo(ManagerType.HEADQUARTER, sede);
        adapter.save(otraVez);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM encargado_sede WHERE k_identificador = ?",
                        Integer.class, persona))
                .isEqualTo(1);
        assertThat(activa("encargado_sede", "k_id_sede", persona, sede)).isTrue();
    }

    @Test
    @DisplayName("por sede devuelve a quienes la tienen activa, enteros, y no a los relevados")
    void porSede() {
        UUID sede = unaSede();
        UUID otra = unaSede();
        UUID activa = unaPersona();
        Manager conDos = Manager.forHeadquarter(activa, sede);
        conDos.assignTo(ManagerType.HEADQUARTER, otra);
        adapter.save(conDos);
        adapter.save(Manager.forHeadquarter(unaPersona(), sede));
        UUID relevada = unaPersona();
        Manager encargado = Manager.forHeadquarter(relevada, sede);
        encargado.deactivate();
        adapter.save(encargado);

        assertThat(adapter.findByHeadquarter(sede))
                .extracting(Manager::getIdPersona)
                .hasSize(2)
                .doesNotContain(relevada);
        // Entero: la otra sede viene con el.
        assertThat(adapter.findByHeadquarter(sede).stream()
                        .filter(m -> m.getIdPersona().equals(activa))
                        .findFirst().orElseThrow().getIdsSede())
                .containsExactlyInAnyOrder(sede, otra);
    }

    @Test
    @DisplayName("por area devuelve a quienes la tienen activa")
    void porArea() {
        UUID area = unArea(unaSede());
        adapter.save(Manager.forServiceArea(unaPersona(), area));
        UUID relevada = unaPersona();
        Manager encargado = Manager.forServiceArea(relevada, area);
        encargado.deactivate();
        adapter.save(encargado);

        assertThat(adapter.findByServiceArea(area)).hasSize(1);
    }

    @Test
    @DisplayName("findAll reune por persona las filas de las dos tablas")
    void todos() {
        UUID persona = unaPersona();
        UUID sede = unaSede();
        Manager encargado = Manager.forHeadquarter(persona, sede);
        encargado.assignTo(ManagerType.SERVICE_AREA, unArea(sede));
        adapter.save(encargado);
        adapter.save(Manager.forHeadquarter(unaPersona(), unaSede()));

        assertThat(adapter.findAll()).hasSize(2);
    }
}
