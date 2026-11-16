package com.malphasos.malphasos.dataload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.ReportSheetComposer;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.infrastructure.output.pdf.ReportHtmlRenderer;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderPersistencePort;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
 * La carga completa por el API, como la hará quien carga, y leída después por las mismas puertas que usa
 * MalphasOS: la hoja de vida del equipo y el informe de su reporte, renderizado como se manda a Gotenberg.
 * Como la cargaban las versiones anteriores —el catálogo solo, la firma del ingeniero, y los tres archivos
 * juntos—, y como se carga desde el 2026-11-16: un solo ZIP, con la firma en el catálogo. Y otra vez, para
 * ver que una carga que salió bien no se repite.
 *
 * <p>Todo en una transacción que se deshace al terminar: MockMvc corre en el hilo de la prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DataLoadEndToEndTest {

    private static final String APLICAR = "/v1/api/data-loads";
    private static final byte[] FIRMA = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PersonPersistencePort personas;
    @Autowired private PersonSignaturePersistencePort firmas;
    @Autowired private ClientPersistencePort clientes;
    @Autowired private ClientEquipmentPersistencePort equipos;
    @Autowired private WorkOrderPersistencePort ordenes;
    @Autowired private ServiceReportPersistencePort reportes;
    @Autowired private LifeSheetServicePort hojasDeVida;
    @Autowired private ServiceReportServicePort informes;
    @Autowired private ReportHtmlRenderer renderizador;

    private static MockMultipartFile archivo(String nombre) {
        return new MockMultipartFile("archivos", nombre, "application/json", EjemplosDeCarga.texto(nombre).getBytes());
    }

    private static MockMultipartHttpServletRequestBuilder conLos(String... nombres) {
        MockMultipartHttpServletRequestBuilder peticion = multipart(APLICAR);
        for (String nombre : nombres) {
            peticion.file(archivo(nombre));
        }
        return peticion;
    }

    private UUID elTensiometro() {
        return equipos.findAll().stream().filter(ClientEquipment::isEstadoActivo)
                .filter(e -> "WA-123456".equals(e.getSerie())).findFirst().orElseThrow().getId();
    }

    private void cargarComoDiceLaGuia() throws Exception {
        UbicacionDelEjemplo.crear(jdbcTemplate);
        mockMvc.perform(conLos(EjemplosDeCarga.CATALOGO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true));

        UUID ingeniero = personas.findAll().stream().filter(p -> p.getCedula().equals("1000000001")).findFirst()
                .orElseThrow().getIdentificador();
        firmas.save(ingeniero, new PersonSignature(FIRMA, LocalDateTime.of(2026, 11, 7, 9, 0)));

        mockMvc.perform(conLos(EjemplosDeCarga.CATALOGO, EjemplosDeCarga.CLIENTE, EjemplosDeCarga.HISTORIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true))
                .andExpect(jsonPath("$.avisos[0].mensaje").value(containsString("provisional")));
    }

    @Test
    @DisplayName("la hoja de vida del equipo cuenta sus dos servicios, del mas reciente al mas viejo, con su ingeniero")
    void laHojaDeVida() throws Exception {
        cargarComoDiceLaGuia();

        List<Intervention> servicios = hojasDeVida.findByEquipment(elTensiometro(), ReadScope.unrestricted()).servicioTecnico();

        assertThat(servicios).extracting(Intervention::tipoServicio)
                .containsExactly(InterventionType.CORRECTIVO, InterventionType.PREVENTIVO);
        assertThat(servicios).extracting(Intervention::responsable).containsOnly("Andrés Gómez Ruiz");
        assertThat(servicios).extracting(Intervention::resultado).containsOnly(InterventionResult.OPERATIVO);
    }

    @Test
    @DisplayName("el informe del reporte se renderiza con su patron, su certificado y la firma del ingeniero")
    void elInforme() throws Exception {
        cargarComoDiceLaGuia();
        ServiceReport preventivo = reportes.findByEquipment(elTensiometro()).stream()
                .min(Comparator.comparing(ServiceReport::getFinalizado)).orElseThrow();

        String html = renderizador.render(ReportSheetComposer.compose(informes.document(preventivo.getId(), ReadScope.unrestricted())));

        assertThat(html).contains("MD-0001").contains("CAL-2025-014").contains("Andrés Gómez Ruiz").contains("data:image/png");
    }

    /** El paquete que entrega el conversor: un ZIP con el catálogo, con la firma del ingeniero, y el cliente. */
    private static MockMultipartFile elZip(byte[] firma) throws java.io.IOException {
        String catalogo = EjemplosDeCarga.texto(EjemplosDeCarga.CATALOGO).replace("\"tipo\": \"ENGINEER\",",
                "\"tipo\": \"ENGINEER\",\n      \"firma\": \"" + java.util.Base64.getEncoder().encodeToString(firma) + "\",");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            for (String[] archivo : new String[][] {{"catalogo.json", catalogo},
                    {"clientes/almendros/cliente.json", EjemplosDeCarga.texto(EjemplosDeCarga.CLIENTE)},
                    {"clientes/almendros/historial.json", EjemplosDeCarga.texto(EjemplosDeCarga.HISTORIAL)}}) {
                zip.putNextEntry(new java.util.zip.ZipEntry(archivo[0]));
                zip.write(archivo[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("archivos", "carga.zip", "application/zip", bytes.toByteArray());
    }

    @Test
    @DisplayName("todo en una carga: un ZIP con la firma del ingeniero en el catalogo, sin pasos intermedios")
    void todoEnUnZip() throws Exception {
        UbicacionDelEjemplo.crear(jdbcTemplate);

        mockMvc.perform(multipart(APLICAR).file(elZip(FIRMA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true));

        UUID ingeniero = personas.findAll().stream().filter(p -> p.getCedula().equals("1000000001")).findFirst()
                .orElseThrow().getIdentificador();
        assertThat(firmas.find(ingeniero)).get().extracting(PersonSignature::imagen).isEqualTo(FIRMA);
        ServiceReport preventivo = reportes.findByEquipment(elTensiometro()).stream()
                .min(Comparator.comparing(ServiceReport::getFinalizado)).orElseThrow();
        assertThat(renderizador.render(ReportSheetComposer.compose(informes.document(preventivo.getId(), ReadScope.unrestricted()))))
                .contains("data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(FIRMA));
    }

    @Test
    @DisplayName("la firma de la carga no sustituye a la que el ingeniero ya guardo")
    void laFirmaGuardadaManda() throws Exception {
        UbicacionDelEjemplo.crear(jdbcTemplate);
        mockMvc.perform(conLos(EjemplosDeCarga.CATALOGO)).andExpect(status().isOk());
        UUID ingeniero = personas.findAll().stream().filter(p -> p.getCedula().equals("1000000001")).findFirst()
                .orElseThrow().getIdentificador();
        firmas.save(ingeniero, new PersonSignature(FIRMA, LocalDateTime.of(2026, 11, 7, 9, 0)));
        byte[] otra = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 8};

        mockMvc.perform(multipart(APLICAR).file(elZip(otra))).andExpect(status().isOk());

        assertThat(firmas.find(ingeniero)).get().extracting(PersonSignature::imagen).isEqualTo(FIRMA);
    }

    @Test
    @DisplayName("la misma carga otra vez responde 422 y no escribe nada: el historial ya esta")
    void otraVez() throws Exception {
        cargarComoDiceLaGuia();
        UUID cliente = clientes.findByDocumento("900000001-1").orElseThrow().getId();
        int antes = ordenes.findByClient(cliente).size();

        mockMvc.perform(conLos(EjemplosDeCarga.CATALOGO, EjemplosDeCarga.CLIENTE, EjemplosDeCarga.HISTORIAL))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.errores[?(@.ruta == '/ordenes/0')].mensaje")
                        .value(hasItem(containsString("no se carga dos veces"))));

        assertThat(ordenes.findByClient(cliente)).hasSize(antes);
    }
}
