package com.malphasos.malphasos.client.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.domain.manager.Manager;
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
 * Ejercita el adaptador contra un PostgreSQL real. Lo que importa aquí es que el mapeo entre la
 * asignación única del agregado y las dos columnas de la tabla satisfaga el {@code CHECK} que las
 * ata al tipo: es la restricción que el esquema original no tenía.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM encargado",
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

    private String unico() {
        return String.valueOf(System.nanoTime() % 10_000_000_000L);
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
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', 'Hospital')
                """,
                cliente, unico().substring(0, 10));
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

    @Test
    @DisplayName("un encargado de sede se guarda con sede y sin area, y el CHECK lo acepta")
    void encargadoDeSede() {
        UUID persona = unaPersona();
        UUID sede = unaSede();

        adapter.save(Manager.forHeadquarter(persona, sede));

        Manager recuperado = adapter.findByPerson(persona).orElseThrow();
        assertThat(recuperado.getTipo()).isEqualTo(ManagerType.HEADQUARTER);
        assertThat(recuperado.getIdSede()).isEqualTo(sede);
        assertThat(recuperado.getIdAreaServicio()).isNull();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT k_id_area_servicio FROM encargado WHERE k_identificador = ?",
                        UUID.class, persona))
                .isNull();
    }

    @Test
    @DisplayName("un encargado de area se guarda con area y sin sede")
    void encargadoDeArea() {
        UUID persona = unaPersona();
        UUID area = unArea(unaSede());

        adapter.save(Manager.forServiceArea(persona, area));

        Manager recuperado = adapter.findByPerson(persona).orElseThrow();
        assertThat(recuperado.getTipo()).isEqualTo(ManagerType.SERVICE_AREA);
        assertThat(recuperado.getIdAreaServicio()).isEqualTo(area);
        assertThat(recuperado.getIdSede()).isNull();
    }

    @Test
    @DisplayName("la identidad del encargado es la de la persona: no hay columna propia")
    void identidadCompartida() {
        UUID persona = unaPersona();
        adapter.save(Manager.forHeadquarter(persona, unaSede()));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM encargado WHERE k_identificador = ?",
                        Integer.class, persona))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("trasladar de sede a area cambia las dos columnas a la vez")
    void trasladar() {
        UUID persona = unaPersona();
        UUID sede = unaSede();
        UUID area = unArea(sede);

        adapter.save(Manager.forHeadquarter(persona, sede));

        Manager encargado = adapter.findByPerson(persona).orElseThrow();
        encargado.reassignTo(ManagerType.SERVICE_AREA, area);
        adapter.save(encargado);

        // Si el mapeo dejara la sede puesta al cambiar de tipo, el CHECK rechazaria la fila.
        Manager recuperado = adapter.findByPerson(persona).orElseThrow();
        assertThat(recuperado.getIdSede()).isNull();
        assertThat(recuperado.getIdAreaServicio()).isEqualTo(area);
    }

    @Test
    @DisplayName("los encargados se recuperan por su sede")
    void porSede() {
        UUID sede = unaSede();
        adapter.save(Manager.forHeadquarter(unaPersona(), sede));
        adapter.save(Manager.forHeadquarter(unaPersona(), sede));

        assertThat(adapter.findByHeadquarter(sede)).hasSize(2);
    }

    @Test
    @DisplayName("relevar conserva la fila con el estado en falso")
    void relevarConservaLaFila() {
        UUID persona = unaPersona();
        adapter.save(Manager.forHeadquarter(persona, unaSede()));

        Manager encargado = adapter.findByPerson(persona).orElseThrow();
        encargado.deactivate();
        adapter.save(encargado);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT b_estado_activo FROM encargado WHERE k_identificador = ?",
                        Boolean.class, persona))
                .isFalse();
        assertThat(adapter.findByPerson(persona)).isPresent();
    }
}
