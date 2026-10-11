package com.malphasos.malphasos.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.application.model.HeadquarterSummary;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterSummaryServicePort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * El resumen de sedes del rediseño (2026-10-11), contra PostgreSQL real: dos consultas para todas las
 * sedes, con el alcance de quien pregunta y lo cerrado incluido con su estado.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {"DELETE FROM area_servicio", "DELETE FROM sede", "DELETE FROM cliente"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class HeadquarterSummaryIntegrationTest {

    @Autowired private HeadquarterSummaryServicePort servicio;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID ciudad;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private UUID unaCiudad() {
        if (ciudad == null) {
            UUID pais = UUID.randomUUID();
            ciudad = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                    pais, CodigoIsoLibre.en(jdbcTemplate), "Pais " + unico());
            jdbcTemplate.update(
                    "INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                    ciudad, "Ciudad " + unico(), pais);
        }
        return ciudad;
    }

    private UUID unCliente(String razonSocial) {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social) VALUES (?, ?, 'NIT_juridico', ?)",
                cliente, unico(), razonSocial);
        return cliente;
    }

    private UUID unaSede(UUID cliente, String nombre, boolean abierta) {
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad, b_estado_activo)
                VALUES (?, ?, '10', '20', '30', ?, ?, ?)
                """,
                sede, nombre, cliente, unaCiudad(), abierta);
        return sede;
    }

    private UUID unArea(UUID sede, String nombre, boolean abierta) {
        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede, b_estado_activo) VALUES (?, ?, ?, ?)",
                area, nombre, sede, abierta);
        return area;
    }

    @Test
    @DisplayName("cada sede trae su cliente, su ciudad, su estado y sus areas por nombre, cerradas incluidas")
    void sedesConAreas() {
        UUID cliente = unCliente("Clinica del Norte");
        // Cuatro, en un orden que no es el alfabetico y con mayusculas mezcladas: con dos, el orden en
        // que la base las devolvia coincidio por azar y una mutacion que quitaba la ordenacion sobrevivio.
        UUID zeta = unaSede(cliente, "Zeta", true);
        UUID principal = unaSede(cliente, "Sede principal", true);
        UUID torre = unaSede(cliente, "antigua torre", false);
        UUID media = unaSede(cliente, "Media", true);
        unArea(principal, "Urgencias", true);
        unArea(principal, "Laboratorio", true);
        unArea(principal, "Bodega", false);

        List<HeadquarterSummary> resumen = servicio.summarize(null, ReadScope.ofClients(Set.of(cliente)));

        // Por nombre, sin distinguir mayusculas: la antigua torre va primero.
        assertThat(resumen).extracting(HeadquarterSummary::id).containsExactly(torre, media, principal, zeta);
        HeadquarterSummary sede = resumen.get(2);
        assertThat(sede.idCliente()).isEqualTo(cliente);
        assertThat(sede.idCiudad()).isEqualTo(ciudad);
        assertThat(sede.abierta()).isTrue();
        assertThat(sede.areas()).extracting(HeadquarterSummary.Area::nombre)
                .containsExactly("Bodega", "Laboratorio", "Urgencias");
        assertThat(sede.areas()).filteredOn(HeadquarterSummary.Area::abierta).hasSize(2);
        assertThat(resumen.getFirst().abierta()).isFalse();
        assertThat(resumen.getFirst().areas()).isEmpty();
    }

    @Test
    @DisplayName("el alcance limita a los clientes visibles, y un cliente ajeno da una lista vacia")
    void alcance() {
        UUID propio = unCliente("Propio");
        UUID ajeno = unCliente("Ajeno");
        unaSede(propio, "Sede propia", true);
        unaSede(ajeno, "Sede ajena", true);
        ReadScope soloPropio = ReadScope.ofClients(Set.of(propio));

        assertThat(servicio.summarize(null, soloPropio)).extracting(HeadquarterSummary::nombre)
                .containsExactly("Sede propia");
        assertThat(servicio.summarize(ajeno, soloPropio)).isEmpty();
        assertThat(servicio.summarize(ajeno, ReadScope.unrestricted())).extracting(HeadquarterSummary::nombre)
                .containsExactly("Sede ajena");
        assertThat(servicio.summarize(null, ReadScope.ofClients(Set.of()))).isEmpty();
    }
}
