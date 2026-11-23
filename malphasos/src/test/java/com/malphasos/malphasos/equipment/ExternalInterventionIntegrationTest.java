package com.malphasos.malphasos.equipment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.UbicacionDelEjemplo;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que otras empresas le hicieron a un equipo (V61), de punta a punta por el API y contra un PostgreSQL
 * real: se anota, se lista y se retira. El equipo es el tensiómetro del ejemplo de carga.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ExternalInterventionIntegrationTest {

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

    private String ruta() {
        return "/v1/api/client-equipments/" + tensiometro + "/external-interventions";
    }

    private static String cuerpo(String dia, String tipo) {
        return """
                {"realizada":"%s","tipoServicio":"%s","descripcion":"Calibración de presión",
                 "empresa":"Metrología S.A.S.","resultado":"OPERATIVO","documento":"CAL-2026-118"}
                """.formatted(dia, tipo);
    }

    @Test
    @DisplayName("se anota con su fecha de registro, y el equipo la lista de la más reciente a la más antigua")
    void anotarYListar() throws Exception {
        mockMvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON).content(cuerpo("2026-10-01", "PREVENTIVO")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON).content(cuerpo("2026-11-20", "CALIBRACION")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresa").value("Metrología S.A.S."))
                .andExpect(jsonPath("$.registrada").isNotEmpty());

        mockMvc.perform(get(ruta()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipoServicio").value("CALIBRACION"))
                .andExpect(jsonPath("$[0].documento").value("CAL-2026-118"))
                .andExpect(jsonPath("$[1].realizada").value("2026-10-01"));
    }

    @Test
    @DisplayName("una fecha futura, un tipo desconocido o sin empresa responden 400")
    void datosInvalidos() throws Exception {
        String manana = LocalDate.now().plusDays(2).toString();
        mockMvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON).content(cuerpo(manana, "CALIBRACION")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_007"));
        mockMvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON).content(cuerpo("2026-11-01", "INSPECCION")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realizada\":\"2026-11-01\",\"tipoServicio\":\"OTRO\",\"descripcion\":\"Algo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("en un equipo que no existe responde 404")
    void equipoQueNoExiste() throws Exception {
        mockMvc.perform(post("/v1/api/client-equipments/" + UUID.randomUUID() + "/external-interventions")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("2026-11-01", "OTRO")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("retirarla la quita de la lista, y una que no existe responde 404 con su código")
    void retirar() throws Exception {
        String creada = mockMvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("2026-11-01", "CORRECTIVO")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(creada, "$.id");

        mockMvc.perform(delete(ruta() + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(ruta())).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete(ruta() + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_023"));
    }

    @Test
    @DisplayName("cuenta para el último servicio del listado, y entra en la hoja de vida con su empresa")
    void enElListadoYEnLaHojaDeVida() throws Exception {
        mockMvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON).content(cuerpo("2026-11-20", "CALIBRACION")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/api/client-equipments/overview").param("ids", tensiometro.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[0].ultimoServicio").value("2026-11-20T00:00:00"))
                .andExpect(jsonPath("$.filas[0].resultado").value("OPERATIVO"));
        mockMvc.perform(get("/v1/api/client-equipments/" + tensiometro + "/life-sheet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicioTecnico[0].origen").value("EXTERNA"))
                .andExpect(jsonPath("$.servicioTecnico[0].empresaExterna").value("Metrología S.A.S."))
                .andExpect(jsonPath("$.servicioTecnico[0].fecha").value("2026-11-20"));
    }
}
