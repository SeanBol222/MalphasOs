package com.malphasos.malphasos.dataload.infrastructure.output.existing;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.dataload.application.model.ExistingRecords;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Lo que ya existe, leído contra PostgreSQL con los puertos de cada módulo. Lo delicado es la cadena del
 * catálogo: un equipo apunta a su modelo, el modelo a una asociación de tipo y marca, y de ahí salen los
 * tres nombres con que se reconoce.
 *
 * <p>Inserta con SQL y comprueba con {@code contains}: la base es compartida, y otras clases dejan sus filas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM equipo_cliente WHERE k_serie LIKE 'CARGA-%'",
            "DELETE FROM modelo WHERE n_nombre_modelo LIKE 'Carga %'",
            "DELETE FROM equipo_patron WHERE k_serie LIKE 'CARGA-%'"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ExistingRecordsAdapterTest {

    @Autowired private ExistingRecordsAdapter adaptador;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    @Test
    @DisplayName("un equipo se lee con su serie y el tipo, la marca y el nombre de su modelo")
    void laCadenaDelCatalogo() {
        String u = unico();
        UUID tipo = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante, m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 0)""", tipo, "Tipo " + u);
        UUID marca = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", marca, "Marca " + u);
        UUID asociacion = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)", asociacion, tipo, marca);
        UUID fabricante = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)", fabricante, "Fabricante " + u);
        UUID modelo = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                modelo, "Carga " + u, fabricante, asociacion);

        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social) VALUES (?, ?, 'NIT_juridico', ?)",
                cliente, u, "Cliente " + u);
        UUID pais = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)", pais, CodigoIsoLibre.en(jdbcTemplate), "Pais " + u);
        UUID ciudad = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)", ciudad, "Ciudad " + u, pais);
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '1', '2', '3', ?, ?)""", sede, "Sede " + u, cliente, ciudad);
        UUID area = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)", area, "Area " + u, sede);
        jdbcTemplate.update("INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), "CARGA-" + u, modelo, area);

        ExistingRecords existentes = adaptador.read();

        ExistingRecords.ModelName nombre = new ExistingRecords.ModelName("Tipo " + u, "Marca " + u, "Carga " + u);
        assertThat(existentes.modelos()).contains(nombre);
        assertThat(existentes.equipos()).contains(new ExistingRecords.EquipmentName(nombre, "CARGA-" + u));
        assertThat(existentes.marcas()).contains("Marca " + u);
        assertThat(existentes.tiposEquipo()).contains("Tipo " + u);
        assertThat(existentes.fabricantes()).contains("Fabricante " + u);
        assertThat(existentes.clientes()).contains(new ExistingRecords.ExistingClient(u,
                java.util.List.of(new ExistingRecords.ExistingSite("Sede " + u, java.util.List.of("Area " + u)))));
    }

    @Test
    @DisplayName("lo retirado no se reconoce: una marca dada de baja no cuenta como existente")
    void loRetiradoNo() {
        String u = unico();
        jdbcTemplate.update("INSERT INTO marca (k_id_marca, n_nombre_marca, b_estado_activo) VALUES (?, ?, false)",
                UUID.randomUUID(), "Retirada " + u);

        assertThat(adaptador.read().marcas()).doesNotContain("Retirada " + u);
    }
}
