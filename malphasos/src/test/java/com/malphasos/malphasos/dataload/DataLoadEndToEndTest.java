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
 * En el orden de la guía: el catálogo solo, la firma del ingeniero, y los tres archivos juntos. Y otra vez,
 * para ver que una carga que salió bien no se repite.
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
