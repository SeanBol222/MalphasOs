package com.malphasos.malphasos.dataload.infrastructure.output.existing;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.dataload.application.model.ExistingRecords;
import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
            "DELETE FROM equipo_patron WHERE k_serie LIKE 'CARGA-%'",
            "DELETE FROM firma_persona WHERE k_identificador IN (SELECT k_identificador FROM persona WHERE n_primer_nombre = 'Carga')",
            "DELETE FROM persona WHERE n_primer_nombre = 'Carga'"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ExistingRecordsAdapterTest {

    @Autowired private ExistingRecordsAdapter adaptador;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PersonSignaturePersistencePort firmas;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

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

        jdbcTemplate.update("""
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio)
                VALUES (?, ?, ?, 'MENSUAL', 'PREVENTIVO')""", UUID.randomUUID(), sede, LocalDate.of(2025, 3, 10));
        jdbcTemplate.update("""
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad,
                                           t_tipo_servicio, b_estado_activo)
                VALUES (?, ?, ?, 'MENSUAL', 'PREVENTIVO', false)""", UUID.randomUUID(), sede, LocalDate.of(2025, 4, 1));

        ExistingRecords existentes = adaptador.read();

        ExistingRecords.ModelName nombre = new ExistingRecords.ModelName("Tipo " + u, "Marca " + u, "Carga " + u);
        assertThat(existentes.modelos()).contains(nombre);
        assertThat(existentes.equipos()).contains(new ExistingRecords.EquipmentName(nombre, "CARGA-" + u));
        assertThat(existentes.marcas()).contains("Marca " + u);
        assertThat(existentes.tiposEquipo()).contains("Tipo " + u);
        assertThat(existentes.fabricantes()).contains("Fabricante " + u);
        assertThat(existentes.clientes()).describedAs("con el día de su orden activa, y no el de la cancelada")
                .contains(new ExistingRecords.ExistingClient(u,
                        List.of(new ExistingRecords.ExistingSite("Sede " + u, List.of("Area " + u))),
                        List.of(LocalDate.of(2025, 3, 10))));
    }

    private String unaPersona(String tipo, boolean conFirma) {
        UUID id = UUID.randomUUID();
        String cedula = unico();
        jdbcTemplate.update("""
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido, t_tipo_persona)
                VALUES (?, ?, 'Carga', 'Prueba', ?)""", id, cedula, tipo);
        if (conFirma) {
            firmas.save(id, new PersonSignature(PNG, LocalDateTime.of(2026, 11, 7, 9, 0)));
        }
        return cedula;
    }

    @Test
    @DisplayName("con firma cuentan solo los ingenieros que la guardaron: un supervisor con firma no hace una orden")
    void lasFirmas() {
        String conFirma = unaPersona("ENGINEER", true);
        String sinFirma = unaPersona("ENGINEER", false);
        String supervisor = unaPersona("SUPERVISOR", true);

        ExistingRecords existentes = adaptador.read();

        assertThat(existentes.cedulas()).contains(conFirma, sinFirma, supervisor);
        assertThat(existentes.cedulasConFirma()).contains(conFirma).doesNotContain(sinFirma, supervisor);
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
