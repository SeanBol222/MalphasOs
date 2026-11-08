package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.UbicacionDelEjemplo;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * La foto de un equipo (V58, 2026-11-08), de punta a punta: se sube por el API, se lee en la hoja de vida
 * y se quita, contra un PostgreSQL real. El equipo es el tensiómetro del ejemplo de carga, cargado por el
 * API como lo cargaría quien migra: así la prueba no arma a mano la cadena del catálogo.
 *
 * <p>Todo en una transacción que se deshace al terminar: MockMvc corre en el hilo de la prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EquipmentPhotoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ClientEquipmentPersistencePort equipos;
    @Autowired private DocumentImageStorePort imagenes;

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

    /** Un PNG de verdad, de un color: dos colores son dos fotos distintas. */
    private static byte[] png(Color color) throws IOException {
        BufferedImage imagen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                imagen.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", bytes);
        return bytes.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder subir(UUID equipo, byte[] contenido) {
        MockMultipartHttpServletRequestBuilder peticion =
                multipart("/v1/api/client-equipments/" + equipo + "/photo")
                        .file(new MockMultipartFile("archivo", "foto.png", "image/png", contenido));
        peticion.with(request -> {
            request.setMethod("PATCH");
            return request;
        });
        return peticion;
    }

    private static String huella(byte[] png) {
        return new DocumentImage(png, "image/png").huella();
    }

    private static String dataUri(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    private String fotoDeLaHojaDeVida() throws Exception {
        String cuerpo = mockMvc.perform(get("/v1/api/client-equipments/" + tensiometro + "/life-sheet"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(cuerpo, "$.identificacion.foto");
    }

    @Test
    @DisplayName("un equipo recién cargado no tiene foto: la hoja de vida la trae nula")
    void sinFoto() throws Exception {
        assertThat(fotoDeLaHojaDeVida()).isNull();
    }

    @Test
    @DisplayName("la foto subida vuelve en la respuesta y en la hoja de vida, como data: URI")
    void subida() throws Exception {
        byte[] foto = png(Color.RED);

        mockMvc.perform(subir(tensiometro, foto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagen").value(dataUri(foto)))
                .andExpect(jsonPath("$.actualizada").exists());

        assertThat(fotoDeLaHojaDeVida()).isEqualTo(dataUri(foto));
    }

    @Test
    @DisplayName("cambiarla deja la nueva, y la vieja se queda en el almacén: un documento congelado podría usarla")
    void cambiada() throws Exception {
        byte[] vieja = png(Color.RED);
        byte[] nueva = png(Color.BLUE);

        mockMvc.perform(subir(tensiometro, vieja)).andExpect(status().isOk());
        mockMvc.perform(subir(tensiometro, nueva)).andExpect(status().isOk());

        assertThat(fotoDeLaHojaDeVida()).isEqualTo(dataUri(nueva));
        assertThat(imagenes.find(huella(vieja))).isPresent();
        assertThat(imagenes.find(huella(nueva))).isPresent();
    }

    @Test
    @DisplayName("quitarla responde 204 y la hoja de vida vuelve al recuadro vacío; la imagen no se borra")
    void quitada() throws Exception {
        byte[] foto = png(Color.GREEN);
        mockMvc.perform(subir(tensiometro, foto)).andExpect(status().isOk());

        mockMvc.perform(delete("/v1/api/client-equipments/" + tensiometro + "/photo"))
                .andExpect(status().isNoContent());

        assertThat(fotoDeLaHojaDeVida()).isNull();
        assertThat(imagenes.find(huella(foto))).isPresent();
    }

    @Test
    @DisplayName("quitar la foto de un equipo que no tiene no falla")
    void quitarSinFoto() throws Exception {
        mockMvc.perform(delete("/v1/api/client-equipments/" + tensiometro + "/photo"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("un JPEG no se acepta: solo PNG, mirado en los bytes y no en lo que dice quien lo sube")
    void unJpeg() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

        mockMvc.perform(subir(tensiometro, jpeg))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_007"));

        assertThat(fotoDeLaHojaDeVida()).isNull();
    }

    @Test
    @DisplayName("la foto de un equipo que no existe responde 404 con su código, no un conflicto de la base")
    void equipoInexistente() throws Exception {
        UUID nadie = UUID.randomUUID();

        mockMvc.perform(subir(nadie, png(Color.RED)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_006"));
        mockMvc.perform(delete("/v1/api/client-equipments/" + nadie + "/photo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_006"));
    }
}
