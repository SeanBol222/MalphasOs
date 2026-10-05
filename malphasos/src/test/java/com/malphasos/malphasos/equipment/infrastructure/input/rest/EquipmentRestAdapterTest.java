package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeclareVerificationsCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.VerificationPointRequest;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.equipment.application.ports.input.*;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.model.commands.CreateModelCommand;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.application.services.model.commands.DescribeModelCommand;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.equipment.domain.exception.CrossClientRelocationException;
import com.malphasos.malphasos.equipment.domain.exception.ModelNotFoundException;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Contrato HTTP del catálogo de equipos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentRestAdapterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @MockitoBean private ManufacturerServicePort manufacturerServicePort;
    @MockitoBean private BrandServicePort brandServicePort;
    @MockitoBean private EquipmentTypeServicePort equipmentTypeServicePort;
    @MockitoBean private EquipmentServicePort equipmentServicePort;
    @MockitoBean private ModelServicePort modelServicePort;
    @MockitoBean private ClientEquipmentServicePort clientEquipmentServicePort;

    private static final UUID ID_TEMPERATURA = UUID.randomUUID();
    private static final UUID ID_GRADOS = UUID.randomUUID();
    private static final UUID ID_HUMEDAD = UUID.randomUUID();
    private static final UUID ID_PORCIENTO = UUID.randomUUID();

    private static final Magnitude TEMPERATURA =
            new Magnitude(ID_TEMPERATURA, "temperatura", "Temperatura", true);
    private static final MeasurementUnit GRADOS =
            new MeasurementUnit(ID_GRADOS, ID_TEMPERATURA, "°C", "grado Celsius", true);
    private static final Magnitude HUMEDAD =
            new Magnitude(ID_HUMEDAD, "humedad_relativa", "Humedad relativa", true);
    private static final MeasurementUnit PORCIENTO =
            new MeasurementUnit(ID_PORCIENTO, ID_HUMEDAD, "%HR", "por ciento de humedad relativa", true);

    /** Un tipo con la modalidad indicada en temperatura, o sin ninguna verificación si llega nula. */
    private EquipmentType unTipo(VerificationMode modalidad) {
        return tipoCon(modalidad == null
                ? List.of()
                : List.of(unaVerificacion(TEMPERATURA, GRADOS, modalidad)));
    }

    private EquipmentType tipoCon(List<TypeVerification> verificaciones) {
        return EquipmentType.rehydrate(UUID.randomUUID(), "Monitor", "Def", "Cuid", "Electronica",
                "Monitoreo", "Paño seco", verificaciones, 150_000L, true);
    }

    /** Las modalidades constantes traen su cantidad y un punto; la variable, ninguno de los dos. */
    private TypeVerification unaVerificacion(
            Magnitude magnitud, MeasurementUnit unidad, VerificationMode modalidad) {

        boolean constante = modalidad == VerificationMode.PATRON_CONSTANTE
                || modalidad == VerificationMode.EQUIPO_CONSTANTE;

        return TypeVerification.of(
                magnitud,
                unidad,
                modalidad,
                constante ? 3 : null,
                constante ? List.of(VerificationPoint.of(new BigDecimal("100"))) : List.of());
    }

    @Test
    @DisplayName("POST de una marca valida responde 201")
    void crearMarca() throws Exception {
        when(brandServicePort.create(any())).thenReturn(Brand.rehydrate(UUID.randomUUID(), "Philips", true));

        mockMvc.perform(post("/v1/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new NamedRequest("Philips"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Philips"));
    }

    @Test
    @DisplayName("una marca sin nombre se rechaza sin llegar al servicio")
    void marcaSinNombre() throws Exception {
        mockMvc.perform(post("/v1/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new NamedRequest("  "))))
                .andExpect(status().isBadRequest());

        verify(brandServicePort, never()).create(any());
    }

    @Test
    @DisplayName("el tipo expone verificable derivado de que haya verificaciones")
    void verificableDerivado() throws Exception {
        when(equipmentTypeServicePort.findAll())
                .thenReturn(List.of(unTipo(VerificationMode.PATRON_CONSTANTE)));

        mockMvc.perform(get("/v1/api/equipment-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificable").value(true))
                .andExpect(jsonPath("$[0].verificaciones[0].modalidad").value("PATRON_CONSTANTE"))
                // El amperaje se miraba aqui hasta V15; ahora es del modelo, y el tipo expone su uso.
                .andExpect(jsonPath("$[0].amperaje").doesNotExist())
                .andExpect(jsonPath("$[0].uso").value("Monitoreo"));
    }

    @Test
    @DisplayName("sin verificaciones, el tipo se expone como no verificable")
    void noVerificable() throws Exception {
        when(equipmentTypeServicePort.findAll()).thenReturn(List.of(unTipo(null)));

        mockMvc.perform(get("/v1/api/equipment-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificable").value(false))
                .andExpect(jsonPath("$[0].verificaciones").isEmpty());
    }

    @Test
    @DisplayName("un tipo con dos magnitudes expone las dos, cada una con su unidad")
    void dosMagnitudes() throws Exception {
        // Es el caso que forzo el cambio del 2026-10-03: un termohigrometro no son dos tipos de equipo.
        when(equipmentTypeServicePort.findAll()).thenReturn(List.of(tipoCon(List.of(
                unaVerificacion(TEMPERATURA, GRADOS, VerificationMode.PATRON_CONSTANTE),
                unaVerificacion(HUMEDAD, PORCIENTO, VerificationMode.PATRON_EQUIPO_VARIABLE)))));

        mockMvc.perform(get("/v1/api/equipment-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificaciones.length()").value(2))
                .andExpect(jsonPath("$[0].verificaciones[0].magnitud").value("Temperatura"))
                .andExpect(jsonPath("$[0].verificaciones[0].unidad").value("°C"))
                .andExpect(jsonPath("$[0].verificaciones[0].cantidadDatos").value(3))
                .andExpect(jsonPath("$[0].verificaciones[0].puntos.length()").value(1))
                .andExpect(jsonPath("$[0].verificaciones[1].magnitud").value("Humedad relativa"))
                .andExpect(jsonPath("$[0].verificaciones[1].unidad").value("%HR"))
                // Con patron y equipo variables no hay cantidad ni puntos, y la respuesta lo refleja.
                .andExpect(jsonPath("$[0].verificaciones[1].cantidadDatos").doesNotExist())
                .andExpect(jsonPath("$[0].verificaciones[1].puntos").isEmpty());
    }

    @Test
    @DisplayName("la ruta de las verificaciones lleva magnitud, unidad, cantidad y puntos")
    void declararVerificaciones() throws Exception {
        // Los cuatro datos viajan juntos a proposito: por separado existiria el instante en que un tipo
        // dice verificar temperatura contra un patron constante sin decir contra que valor.
        UUID id = UUID.randomUUID();
        ArgumentCaptor<DeclareVerificationsCommand> comando =
                ArgumentCaptor.forClass(DeclareVerificationsCommand.class);
        when(equipmentTypeServicePort.declareVerifications(any()))
                .thenReturn(unTipo(VerificationMode.PATRON_CONSTANTE));

        mockMvc.perform(patch("/v1/api/equipment-types/" + id + "/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new DeclareVerificationsRequest(
                                List.of(new TypeVerificationRequest(
                                        ID_TEMPERATURA,
                                        ID_GRADOS,
                                        VerificationMode.PATRON_CONSTANTE,
                                        5,
                                        List.of(new VerificationPointRequest(new BigDecimal("100")),
                                                new VerificationPointRequest(new BigDecimal("-20")))))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificaciones[0].cantidadDatos").value(3));

        verify(equipmentTypeServicePort).declareVerifications(comando.capture());
        assertThat(comando.getValue().verificaciones()).hasSize(1);
        assertThat(comando.getValue().verificaciones().getFirst().cantidadDatos()).isEqualTo(5);
        assertThat(comando.getValue().verificaciones().getFirst().magnitudId()).isEqualTo(ID_TEMPERATURA);
        assertThat(comando.getValue().verificaciones().getFirst().unidadId()).isEqualTo(ID_GRADOS);
        assertThat(comando.getValue().verificaciones().getFirst().puntos()).hasSize(2);
    }

    @Test
    @DisplayName("una verificacion sin magnitud no llega al servicio")
    void verificacionSinMagnitudSeRechaza() {
        // La validacion del cuerpo corre antes que el servicio, de modo que esto ni se intenta: es lo
        // que evita un 500 por una referencia ausente.
        assertThatCode(() -> mockMvc.perform(patch("/v1/api/equipment-types/" + UUID.randomUUID()
                                + "/verifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(new DeclareVerificationsRequest(
                                        List.of(new TypeVerificationRequest(
                                                null,
                                                ID_GRADOS,
                                                VerificationMode.PATRON_CONSTANTE,
                                                3,
                                                List.of(new VerificationPointRequest(
                                                        new BigDecimal("100")))))))))
                        .andExpect(status().isBadRequest()))
                .doesNotThrowAnyException();

        verify(equipmentTypeServicePort, never()).declareVerifications(any());
    }

    @Test
    @DisplayName("una cantidad de lecturas fuera de rango se rechaza sin llegar al servicio")
    void cantidadFueraDeRango() {
        assertThatCode(() -> mockMvc.perform(patch("/v1/api/equipment-types/" + UUID.randomUUID()
                                + "/verifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(new DeclareVerificationsRequest(
                                        List.of(new TypeVerificationRequest(
                                                ID_TEMPERATURA,
                                                ID_GRADOS,
                                                VerificationMode.PATRON_CONSTANTE,
                                                0,
                                                List.of()))))))
                        .andExpect(status().isBadRequest()))
                .doesNotThrowAnyException();

        verify(equipmentTypeServicePort, never()).declareVerifications(any());
    }

    @Test
    @DisplayName("las verificaciones tienen ruta propia, y la lista vacia revierte el tipo")
    void quitarVerificaciones() throws Exception {
        UUID id = UUID.randomUUID();
        when(equipmentTypeServicePort.declareVerifications(any())).thenReturn(unTipo(null));

        mockMvc.perform(patch("/v1/api/equipment-types/" + id + "/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new DeclareVerificationsRequest(List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificable").value(false));
    }

    @Test
    @DisplayName("un valor de mantenimiento negativo se rechaza")
    void valorNegativo() throws Exception {
        mockMvc.perform(post("/v1/api/equipment-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new EquipmentTypeCreateRequest(
                                "Monitor", "Def", "Cuid", "Electronica", null, null,
                                List.of(), -1L))))
                .andExpect(status().isBadRequest());

        verify(equipmentTypeServicePort, never()).create(any());
    }

    @Test
    @DisplayName("la asociacion marca-tipo no ofrece operacion de cambio")
    void asociacionSinPatch() throws Exception {
        // Sus dos referencias son inmutables: si esta mal se retira y se crea la correcta.
        mockMvc.perform(patch("/v1/api/equipments/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("el alta de una unidad toma el area de la ruta, no del cuerpo")
    void areaDesdeLaRuta() throws Exception {
        UUID area = UUID.randomUUID();
        UUID modelo = UUID.randomUUID();
        when(clientEquipmentServicePort.register(any())).thenReturn(ClientEquipment.rehydrate(
                UUID.randomUUID(), "SN-001", modelo, area, null, null, null, null, null, true));

        mockMvc.perform(post("/v1/api/service-areas/" + area + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientEquipmentRegisterRequest(
                                "SN-001", modelo, "INV-42", LocalDate.now().minusYears(1), 5_000_000L, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idAreaServicio").value(area.toString()));

        ArgumentCaptor<RegisterClientEquipmentCommand> comando =
                ArgumentCaptor.forClass(RegisterClientEquipmentCommand.class);
        verify(clientEquipmentServicePort).register(comando.capture());

        assertThat(comando.getValue().idAreaServicio()).isEqualTo(area);
    }

    @Test
    @DisplayName("una fecha de compra futura se rechaza sin llegar al servicio")
    void fechaFutura() throws Exception {
        mockMvc.perform(post("/v1/api/service-areas/" + UUID.randomUUID() + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientEquipmentRegisterRequest(
                                "SN-001", UUID.randomUUID(), null, LocalDate.now().plusDays(1), null, null, null))))
                .andExpect(status().isBadRequest());

        verify(clientEquipmentServicePort, never()).register(any());
    }

    @Test
    @DisplayName("un area de servicio inexistente responde 404, no un conflicto de datos")
    void areaInexistente() throws Exception {
        UUID area = UUID.randomUUID();
        when(clientEquipmentServicePort.register(any()))
                .thenThrow(new ServiceAreaNotFoundException(area));

        mockMvc.perform(post("/v1/api/service-areas/" + area + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientEquipmentRegisterRequest(
                                "SN-001", UUID.randomUUID(), null, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_009"));
    }

    @Test
    @DisplayName("el alta de un modelo exige su nombre, y no llega al servicio sin el")
    void modeloSinNombre() throws Exception {
        // La validacion del cuerpo corre antes que el servicio: es lo que evita un 500 por un nombre
        // en blanco, que la columna rechazaria.
        mockMvc.perform(post("/v1/api/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ModelCreateRequest(
                                "  ", "INV-1", UUID.randomUUID(), UUID.randomUUID(), null))))
                .andExpect(status().isBadRequest());

        verify(modelServicePort, never()).create(any());
    }

    @Test
    @DisplayName("el alta manda el nombre al servicio, y la respuesta lo devuelve")
    void modeloConNombre() throws Exception {
        UUID fabricante = UUID.randomUUID();
        UUID equipo = UUID.randomUUID();
        ArgumentCaptor<CreateModelCommand> comando = ArgumentCaptor.forClass(CreateModelCommand.class);
        when(modelServicePort.create(any())).thenReturn(
                Model.rehydrate(UUID.randomUUID(), "IdeaPad 3", "INV-1", fabricante, equipo, TechnicalSheet.EMPTY, true));

        mockMvc.perform(post("/v1/api/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ModelCreateRequest(
                                "IdeaPad 3", "INV-1", fabricante, equipo, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("IdeaPad 3"));

        verify(modelServicePort).create(comando.capture());
        assertThat(comando.getValue().nombre()).isEqualTo("IdeaPad 3");
    }

    @Test
    @DisplayName("renombrar un modelo tiene su ruta, igual que renombrar una marca")
    void renombrarModelo() throws Exception {
        UUID id = UUID.randomUUID();
        when(modelServicePort.rename(any())).thenReturn(
                Model.rehydrate(id, "IdeaPad 5", null, UUID.randomUUID(), UUID.randomUUID(), TechnicalSheet.EMPTY, true));

        mockMvc.perform(patch("/v1/api/models/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new NamedRequest("IdeaPad 5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("IdeaPad 5"));
    }

    @Test
    @DisplayName("la ficha tecnica tiene su ruta, y llega entera al servicio")
    void fichaTecnica() throws Exception {
        UUID id = UUID.randomUUID();
        ArgumentCaptor<DescribeModelCommand> comando = ArgumentCaptor.forClass(DescribeModelCommand.class);
        when(modelServicePort.describe(any())).thenReturn(Model.rehydrate(id, "GS14", null, UUID.randomUUID(),
                UUID.randomUUID(), new TechnicalSheet(RiskClass.I, null, "Baterias", null, null,
                        new BigDecimal("0.50"), null), true));

        mockMvc.perform(patch("/v1/api/models/" + id + "/technical-sheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riesgo\":\"I\",\"alimentacion\":\"Baterias\",\"amperaje\":0.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fichaTecnica.riesgo").value("I"))
                .andExpect(jsonPath("$.fichaTecnica.alimentacion").value("Baterias"));

        verify(modelServicePort).describe(comando.capture());
        assertThat(comando.getValue().id()).isEqualTo(id);
        assertThat(comando.getValue().fichaTecnica().riesgo()).isEqualTo(RiskClass.I);
        assertThat(comando.getValue().fichaTecnica().voltaje()).isNull();
    }

    @Test
    @DisplayName("una ficha con un voltaje en cero o un amperaje con tres decimales no llega al servicio")
    void fichaInvalida() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/v1/api/models/" + id + "/technical-sheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voltaje\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/v1/api/models/" + id + "/technical-sheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amperaje\":1.255}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/v1/api/models/" + id + "/technical-sheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riesgo\":\"IV\"}"))
                .andExpect(status().isBadRequest());

        verify(modelServicePort, never()).describe(any());
    }

    @Test
    @DisplayName("un modelo inexistente responde 404 con el codigo del catalogo")
    void modeloInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(modelServicePort.findById(id)).thenThrow(new ModelNotFoundException(id));

        mockMvc.perform(get("/v1/api/models/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_005"));
    }

    @Test
    @DisplayName("una regla del servicio se traduce a 400, no a 500")
    void reglaDelServicio() throws Exception {
        when(clientEquipmentServicePort.register(any()))
                .thenThrow(new IllegalArgumentException("No se puede incorporar una unidad de un modelo retirado"));

        mockMvc.perform(post("/v1/api/service-areas/" + UUID.randomUUID() + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientEquipmentRegisterRequest(
                                "SN-001", UUID.randomUUID(), null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_007"));
    }

    @Test
    @DisplayName("trasladar a un area de otro cliente responde 409 con el codigo propio")
    void trasladarAAreaDeOtroCliente() throws Exception {
        UUID id = UUID.randomUUID();
        UUID areaDestino = UUID.randomUUID();
        when(clientEquipmentServicePort.relocate(any())).thenThrow(new CrossClientRelocationException(
                id, areaDestino, UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(patch("/v1/api/client-equipments/" + id + "/service-area/" + areaDestino))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_010"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("los siete recursos aparecen en el grupo de OpenAPI del modulo")
    void recursosDocumentados() throws Exception {
        // Un patron de grupo que no case con ninguna ruta real no falla: deja el recurso fuera de
        // Swagger en silencio. Tres de estos siete se habian quedado fuera por esa via, y el
        // septimo -- el catalogo metrologico, del 2026-10-03 -- se quedo fuera tambien al nacer:
        // se escribio el controlador y nadie anadio su patron al grupo. Esta prueba lo delato.
        String docs = mockMvc.perform(get("/v3/api-docs/equipment"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(docs)
                .describedAs("Todo recurso del catalogo debe estar en su grupo de OpenAPI")
                .contains("/v1/api/manufacturers")
                .contains("/v1/api/brands")
                .contains("/v1/api/equipment-types")
                .contains("/v1/api/equipments")
                .contains("/v1/api/models")
                .contains("/v1/api/client-equipments")
                .contains("/v1/api/magnitudes");
    }

    @Test
    @DisplayName("DELETE de una unidad responde 204 y la da de baja sin borrarla")
    void darDeBaja() throws Exception {
        mockMvc.perform(delete("/v1/api/client-equipments/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());

        verify(clientEquipmentServicePort).decommission(any());
    }
}
