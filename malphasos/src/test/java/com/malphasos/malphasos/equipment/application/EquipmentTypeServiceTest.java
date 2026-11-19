package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.MetrologyCatalogPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.VerificationStandardsPersistencePort;
import com.malphasos.malphasos.equipment.application.services.equipmentType.EquipmentTypeService;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.CreateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeclareVerificationsCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.TypeVerificationCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.VerificationPointCommand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.exception.MagnitudeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.MeasurementUnitNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.UnitOutsideMagnitudeException;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las tres reglas cruzadas del catálogo metrológico, que ninguna otra prueba ejercía.
 *
 * <p><b>Esta clase existe porque una mutación lo destapó el 2026-10-04.</b> Se desactivó la guarda
 * «la unidad tiene que ser de esa magnitud» —se sustituyó su condición por {@code false}— y la batería
 * entera siguió verde: **842 pruebas, cero fallos**. El motivo era simple y peor de lo que parecía:
 * <b>{@code EquipmentTypeService} no tenía ninguna prueba</b>. Ninguna clase de {@code src/test}
 * mencionaba su nombre.
 *
 * <p>Lo que había alrededor y por qué no bastaba:
 *
 * <ul>
 *   <li>{@code EquipmentRestAdapterTest} <b>simula el puerto de entrada</b>, de modo que ejercita el
 *       contrato HTTP y nunca entra al servicio.
 *   <li>{@code EquipmentCatalogPersistenceTest} entra por el <b>adaptador</b> y construye las
 *       verificaciones a mano, así que tampoco pasa por aquí.
 *   <li>{@code CatalogAggregatesTest} cubre el agregado, que no consulta el catálogo: <b>no puede</b>
 *       comprobar que una magnitud exista.
 * </ul>
 *
 * <p>Las tres reglas viven en el servicio y no en el agregado por una razón que conviene no perder: un
 * agregado no consulta nada. Sin ellas, el llamante recibiría un conflicto de integridad genérico de
 * PostgreSQL en vez de «esa magnitud no existe» — y en el caso de la tercera, un 409 con código propio
 * en vez de un 500.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentTypeServiceTest {

    private static final UUID ID_PRESION = UUID.randomUUID();
    private static final UUID ID_TEMPERATURA = UUID.randomUUID();

    private static final Magnitude PRESION = new Magnitude(ID_PRESION, "presion", "Presión", true);
    private static final MeasurementUnit MMHG =
            new MeasurementUnit(UUID.randomUUID(), ID_PRESION, "mmHg", "milímetro de mercurio", true);
    private static final Magnitude TEMPERATURA =
            new Magnitude(ID_TEMPERATURA, "temperatura", "Temperatura", true);
    private static final MeasurementUnit CELSIUS =
            new MeasurementUnit(UUID.randomUUID(), ID_TEMPERATURA, "°C", "grado Celsius", true);

    @Mock private EquipmentTypePersistencePort typePort;
    @Mock private MetrologyCatalogPersistencePort catalogPort;
    @Mock private EventDispatcherPort dispatcher;
    @Mock private VerificationStandardsPersistencePort standardsPort;

    private EquipmentTypeService service() {
        return new EquipmentTypeService(typePort, catalogPort, standardsPort, dispatcher);
    }

    /** Un alta con la verificación que se le pase, para no repetir los seis campos obligatorios. */
    private CreateEquipmentTypeCommand alta(TypeVerificationCommand... verificaciones) {
        return new CreateEquipmentTypeCommand(
                "Tensiometro", "Definicion", "Cuidados", "Electronica", null, null,
                List.of(verificaciones), 150_000L);
    }

    private TypeVerificationCommand enPresion(UUID magnitud, UUID unidad) {
        return new TypeVerificationCommand(
                magnitud, unidad, VerificationMode.PATRON_CONSTANTE, 3,
                List.of(new VerificationPointCommand(new BigDecimal("100"))));
    }

    @Test
    @DisplayName("una magnitud que no existe se rechaza, y no se guarda nada")
    void magnitudInexistente() {
        UUID inventada = UUID.randomUUID();
        when(catalogPort.findMagnitudeById(inventada)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(alta(enPresion(inventada, MMHG.id()))))
                .isInstanceOf(MagnitudeNotFoundException.class);

        verify(typePort, never()).save(any());
    }

    @Test
    @DisplayName("una unidad que no existe se rechaza con su propio fallo, no con el de la magnitud")
    void unidadInexistente() {
        // Dos fallos distintos a proposito: quien llama tiene que saber cual de las dos referencias
        // esta mal, y «datos invalidos» no se lo dice.
        UUID inventada = UUID.randomUUID();
        when(catalogPort.findMagnitudeById(ID_PRESION)).thenReturn(Optional.of(PRESION));
        when(catalogPort.findUnitById(inventada)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(alta(enPresion(ID_PRESION, inventada))))
                .isInstanceOf(MeasurementUnitNotFoundException.class);

        verify(typePort, never()).save(any());
    }

    @Test
    @DisplayName("una unidad de OTRA magnitud se rechaza: medir temperatura en mmHg no se escribe")
    void unidadDeOtraMagnitud() {
        // ESTA ES LA QUE NINGUNA PRUEBA EJERCIA. Desactivar esta guarda dejaba la bateria entera en
        // verde: 842 pruebas, cero fallos. Hasta V48 el esquema lo impedia tambien, con una foranea
        // compuesta al par (magnitud, unidad), pero devolvia un conflicto de integridad generico; esta
        // guarda es la que da un 409 con codigo propio.
        when(catalogPort.findMagnitudeById(ID_TEMPERATURA)).thenReturn(Optional.of(TEMPERATURA));
        when(catalogPort.findUnitById(MMHG.id())).thenReturn(Optional.of(MMHG));

        assertThatThrownBy(() -> service().create(alta(enPresion(ID_TEMPERATURA, MMHG.id()))))
                .isInstanceOf(UnitOutsideMagnitudeException.class)
                .hasMessageContaining(MMHG.id().toString());

        verify(typePort, never()).save(any());
    }

    @Test
    @DisplayName("con la magnitud y su unidad, la verificacion se guarda con las piezas dentro")
    void altaValida() {
        // Y comprueba la otra mitad de la decision: la verificacion EMBEBE la magnitud y la unidad, no
        // sus identificadores, y las trae la misma consulta que las valida -- no hay una segunda.
        when(catalogPort.findMagnitudeById(ID_PRESION)).thenReturn(Optional.of(PRESION));
        when(catalogPort.findUnitById(MMHG.id())).thenReturn(Optional.of(MMHG));
        when(typePort.save(any(EquipmentType.class))).thenAnswer(i -> i.getArgument(0));

        EquipmentType guardado = service().create(alta(enPresion(ID_PRESION, MMHG.id())));

        assertThat(guardado.isVerificable()).isTrue();
        assertThat(guardado.verificacionesActivas()).singleElement().satisfies(verificacion -> {
            assertThat(verificacion.magnitud().nombre()).isEqualTo("Presión");
            assertThat(verificacion.unidad().simbolo()).isEqualTo("mmHg");
            assertThat(verificacion.cantidadDatos()).isEqualTo(3);
            assertThat(verificacion.puntosActivos()).hasSize(1);
        });
        verify(catalogPort).findMagnitudeById(ID_PRESION);
        verify(catalogPort).findUnitById(MMHG.id());
    }

    @Test
    @DisplayName("la tolerancia del comando llega a la verificacion guardada")
    void altaConTolerancia() {
        when(catalogPort.findMagnitudeById(ID_PRESION)).thenReturn(Optional.of(PRESION));
        when(catalogPort.findUnitById(MMHG.id())).thenReturn(Optional.of(MMHG));
        when(typePort.save(any(EquipmentType.class))).thenAnswer(i -> i.getArgument(0));

        EquipmentType guardado = service().create(alta(new TypeVerificationCommand(
                ID_PRESION, MMHG.id(), VerificationMode.PATRON_CONSTANTE, 3,
                List.of(new VerificationPointCommand(new BigDecimal("100"))), new BigDecimal("2"))));

        assertThat(guardado.verificacionesActivas().getFirst().tolerancia()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("declarar verificaciones comprueba las referencias de TODAS, no solo de la primera")
    void declararComprobandoCadaUna() {
        // Un recorrido que valide solo el primer elemento pasaria la prueba de arriba y dejaria entrar
        // la segunda referencia inventada. Se comprueba con dos.
        UUID id = UUID.randomUUID();
        UUID inventada = UUID.randomUUID();
        when(typePort.findById(id)).thenReturn(Optional.of(EquipmentType.rehydrate(
                id, "Tensiometro", "D", "C", "E", null, null, List.of(), 1000L, true)));
        when(catalogPort.findMagnitudeById(ID_PRESION)).thenReturn(Optional.of(PRESION));
        when(catalogPort.findUnitById(MMHG.id())).thenReturn(Optional.of(MMHG));
        when(catalogPort.findMagnitudeById(inventada)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().declareVerifications(new DeclareVerificationsCommand(
                        id,
                        List.of(enPresion(ID_PRESION, MMHG.id()), enPresion(inventada, CELSIUS.id())))))
                .isInstanceOf(MagnitudeNotFoundException.class);

        verify(typePort, never()).save(any());
    }

    @Test
    @DisplayName("la lista vacia deja de verificar el tipo, y no toca el catalogo")
    void dejarDeVerificar() {
        UUID id = UUID.randomUUID();
        when(typePort.findById(id)).thenReturn(Optional.of(EquipmentType.rehydrate(
                id, "Tensiometro", "D", "C", "E", null, null, List.of(), 1000L, true)));
        when(typePort.save(any(EquipmentType.class))).thenAnswer(i -> i.getArgument(0));

        EquipmentType guardado =
                service().declareVerifications(new DeclareVerificationsCommand(id, List.of()));

        assertThat(guardado.isVerificable()).isFalse();
        // Sin verificaciones no hay nada que consultar: pedir el catalogo seria trabajo inutil.
        verify(catalogPort, never()).findMagnitudeById(any());
    }

    /** Un tensiómetro guardado con una verificación de presión a 100 mmHg y esta tolerancia. */
    private EquipmentType tensiometroConPresion(UUID id, TypeVerification presion) {
        return EquipmentType.rehydrate(
                id, "Tensiometro", "D", "C", "E", null, null, List.of(presion), 1000L, true);
    }

    @Test
    @DisplayName("cambiar la tolerancia rehace la verificacion, y la nueva hereda los patrones de la vieja")
    void lasNuevasHeredanLosPatrones() {
        // Declarar retira y crea con identificador nuevo aunque solo cambie la tolerancia. Sin heredar,
        // cada edicion del tipo dejaria sus verificaciones sin patrones (V60).
        UUID id = UUID.randomUUID();
        TypeVerification vieja = TypeVerification.rehydrate(UUID.randomUUID(), PRESION, MMHG,
                VerificationMode.PATRON_CONSTANTE, 3, List.of(VerificationPoint.of(new BigDecimal("100"))), true,
                new BigDecimal("2"));
        UUID simulador = UUID.randomUUID();
        when(typePort.findById(id)).thenReturn(Optional.of(tensiometroConPresion(id, vieja)));
        when(catalogPort.findMagnitudeById(ID_PRESION)).thenReturn(Optional.of(PRESION));
        when(catalogPort.findUnitById(MMHG.id())).thenReturn(Optional.of(MMHG));
        when(catalogPort.findMagnitudeById(ID_TEMPERATURA)).thenReturn(Optional.of(TEMPERATURA));
        when(catalogPort.findUnitById(CELSIUS.id())).thenReturn(Optional.of(CELSIUS));
        when(typePort.save(any(EquipmentType.class))).thenAnswer(i -> i.getArgument(0));
        when(standardsPort.findByVerification(vieja.id()))
                .thenReturn(VerificationStandards.rehydrate(vieja.id(), List.of(simulador)));

        EquipmentType guardado = service().declareVerifications(new DeclareVerificationsCommand(id, List.of(
                new TypeVerificationCommand(ID_PRESION, MMHG.id(), VerificationMode.PATRON_CONSTANTE, 3,
                        List.of(new VerificationPointCommand(new BigDecimal("100"))), new BigDecimal("3")),
                enPresion(ID_TEMPERATURA, CELSIUS.id()))));

        TypeVerification nueva = guardado.verificacionesActivas().stream()
                .filter(v -> v.magnitud().id().equals(ID_PRESION)).findFirst().orElseThrow();
        assertThat(nueva.id()).isNotEqualTo(vieja.id());

        // Una sola vez: la de temperatura no tenia antecesora y no hereda nada.
        ArgumentCaptor<VerificationStandards> guardados = ArgumentCaptor.forClass(VerificationStandards.class);
        verify(standardsPort).save(guardados.capture());
        assertThat(guardados.getValue().getIdVerificacion()).isEqualTo(nueva.id());
        assertThat(guardados.getValue().getPatrones()).containsExactly(simulador);
    }

    @Test
    @DisplayName("declarar lo mismo no rehace nada, y no toca los patrones")
    void declararLoMismoNoHereda() {
        UUID id = UUID.randomUUID();
        TypeVerification actual = TypeVerification.rehydrate(UUID.randomUUID(), PRESION, MMHG,
                VerificationMode.PATRON_CONSTANTE, 3, List.of(VerificationPoint.of(new BigDecimal("100"))), true);
        when(typePort.findById(id)).thenReturn(Optional.of(tensiometroConPresion(id, actual)));
        when(catalogPort.findMagnitudeById(ID_PRESION)).thenReturn(Optional.of(PRESION));
        when(catalogPort.findUnitById(MMHG.id())).thenReturn(Optional.of(MMHG));
        when(typePort.save(any(EquipmentType.class))).thenAnswer(i -> i.getArgument(0));

        service().declareVerifications(new DeclareVerificationsCommand(id, List.of(enPresion(ID_PRESION, MMHG.id()))));

        verify(standardsPort, never()).findByVerification(any());
        verify(standardsPort, never()).save(any());
    }
}
