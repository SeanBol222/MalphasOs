package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.UbicacionDelEjemplo;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que se le hizo a un equipo fuera de MalphasOS (V61, 2026-11-23): la tabla y lo que rechaza, contra un
 * PostgreSQL real. El equipo es el tensiómetro del ejemplo de carga, y todo se deshace al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ExternalInterventionSchemaTest {

    private static final String INSERT = """
            INSERT INTO intervencion_externa (k_id_intervencion_externa, k_id_equipo_cliente, f_realizada,
                                              t_tipo_servicio, t_descripcion, n_empresa, t_resultado)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ClientEquipmentPersistencePort equipos;

    private UUID tensiometro;

    @BeforeEach
    void unEquipo() throws Exception {
        UbicacionDelEjemplo.crear(jdbcTemplate);
        mockMvc.perform(multipart("/v1/api/data-loads")
                        .file(archivo(EjemplosDeCarga.CATALOGO))
                        .file(archivo(EjemplosDeCarga.CLIENTE)))
                .andExpect(status().isOk());
        tensiometro = equipos.findAll().stream().filter(ClientEquipment::isEstadoActivo)
                .filter(e -> "WA-123456".equals(e.getSerie())).findFirst().orElseThrow().getId();
    }

    private static MockMultipartFile archivo(String nombre) {
        return new MockMultipartFile("archivos", nombre, "application/json", EjemplosDeCarga.texto(nombre).getBytes());
    }

    private UUID insertar(UUID equipo, String tipo, String descripcion, String empresa, String resultado) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(INSERT, id, equipo, LocalDate.of(2026, 11, 20), tipo, descripcion, empresa, resultado);
        return id;
    }

    @Test
    @DisplayName("una calibración de otra empresa entra con lo mínimo, activa y con su fecha de registro")
    void conLoMinimo() {
        UUID id = insertar(tensiometro, "CALIBRACION", "Calibración de presión", "Metrología S.A.S.", null);

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT b_estado_activo, f_registrada, n_documento FROM intervencion_externa"
                        + " WHERE k_id_intervencion_externa = ?", id);
        assertThat(fila.get("b_estado_activo")).isEqualTo(true);
        assertThat(fila.get("f_registrada")).isNotNull();
        assertThat(fila.get("n_documento")).isNull();
    }

    @Test
    @DisplayName("admite OTRO además de los tres tipos de un reporte, y nada más")
    void elTipo() {
        insertar(tensiometro, "OTRO", "Actualización de software", "Fabricante", null);

        assertThatThrownBy(() -> insertar(tensiometro, "INSPECCION", "Algo", "Alguien", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHK_intervencion_externa_tipo");
    }

    @Test
    @DisplayName("el resultado es opcional, y si va es uno de los tres de un reporte")
    void elResultado() {
        insertar(tensiometro, "PREVENTIVO", "Limpieza", "Otra empresa", "OPERATIVO_CON_RESTRICCIONES");

        assertThatThrownBy(() -> insertar(tensiometro, "PREVENTIVO", "Limpieza", "Otra empresa", "BIEN"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHK_intervencion_externa_resultado");
    }

    @Test
    @DisplayName("qué se hizo no puede quedar en blanco")
    void sinDescripcion() {
        assertThatThrownBy(() -> insertar(tensiometro, "PREVENTIVO", "   ", "Otra empresa", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHK_intervencion_externa_descripcion");
    }

    @Test
    @DisplayName("quién lo hizo tampoco")
    void sinEmpresa() {
        assertThatThrownBy(() -> insertar(tensiometro, "PREVENTIVO", "Limpieza", " ", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHK_intervencion_externa_empresa");
    }

    @Test
    @DisplayName("cuelga de un equipo que existe")
    void deUnEquipoQueExiste() {
        assertThatThrownBy(() -> insertar(UUID.randomUUID(), "PREVENTIVO", "Limpieza", "Otra empresa", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_intervencion_externa_equipo");
    }

    @Test
    @DisplayName("el historial de un equipo se lee por su índice, equipo y fecha")
    void elIndice() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'"
                                + " AND indexname = 'IX_intervencion_externa_equipo_fecha'",
                        String.class))
                .contains("k_id_equipo_cliente, f_realizada");
    }
}
