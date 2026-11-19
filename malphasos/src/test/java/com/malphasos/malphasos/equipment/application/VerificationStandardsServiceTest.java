package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ReferenceStandardPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.VerificationStandardsPersistencePort;
import com.malphasos.malphasos.equipment.application.services.verificationStandards.VerificationStandardsService;
import com.malphasos.malphasos.equipment.application.services.verificationStandards.commands.AssignVerificationStandardsCommand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.VerificationNotFoundException;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las reglas que el servicio de patrones por verificación comprueba fuera del agregado.
 *
 * <p>Cada guarda se prueba con el camino que guardaría estubado como si fuera a funcionar —el almacén
 * devuelve la verificación y guarda lo que recibe—, y se verifica que no se guardó nada.
 */
@ExtendWith(MockitoExtension.class)
class VerificationStandardsServiceTest {

    private static final UUID ID_PRESION = UUID.randomUUID();
    private static final Magnitude PRESION = new Magnitude(ID_PRESION, "presion", "Presión", true);
    private static final MeasurementUnit MMHG =
            new MeasurementUnit(UUID.randomUUID(), ID_PRESION, "mmHg", "milímetro de mercurio", true);

    @Mock private VerificationStandardsPersistencePort standardsPort;
    @Mock private EquipmentTypePersistencePort typePort;
    @Mock private ReferenceStandardPersistencePort referencePort;
    @Mock private EventDispatcherPort dispatcher;

    @InjectMocks private VerificationStandardsService service;

    private final UUID idTipo = UUID.randomUUID();
    private TypeVerification presion;
    private TypeVerification retirada;

    @BeforeEach
    void unTensiometro() {
        presion = TypeVerification.rehydrate(UUID.randomUUID(), PRESION, MMHG,
                VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of(), true);
        retirada = TypeVerification.rehydrate(UUID.randomUUID(), PRESION, MMHG,
                VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of(), false);
        lenient().when(typePort.findById(idTipo)).thenReturn(Optional.of(tipo(true)));
        lenient().when(standardsPort.findByVerification(any()))
                .thenAnswer(i -> VerificationStandards.empty(i.getArgument(0)));
        lenient().when(standardsPort.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private EquipmentType tipo(boolean activo) {
        return EquipmentType.rehydrate(
                idTipo, "Tensiometro", "D", "C", "E", null, null, List.of(retirada, presion), 1000L, activo);
    }

    private ReferenceStandard patron(boolean activo) {
        return ReferenceStandard.rehydrate(UUID.randomUUID(), "Simulador", null, "S-" + System.nanoTime(), "C-1",
                LocalDate.of(2027, 1, 1), ReferenceStandardSheet.VACIA, activo);
    }

    private AssignVerificationStandardsCommand asignar(UUID verificacion, UUID... patrones) {
        return new AssignVerificationStandardsCommand(idTipo, verificacion, List.of(patrones));
    }

    @Test
    @DisplayName("con verificacion vigente del tipo y patrones vigentes, se guarda y se publica")
    void asignaYPublica() {
        ReferenceStandard simulador = patron(true);
        when(referencePort.findById(simulador.getId())).thenReturn(Optional.of(simulador));

        VerificationStandards guardados = service.assign(asignar(presion.id(), simulador.getId()));

        assertThat(guardados.getPatrones()).containsExactly(simulador.getId());
        verify(dispatcher).dispatchAll(any());
    }

    @Test
    @DisplayName("un tipo que no existe es 404 del tipo")
    void tipoInexistente() {
        UUID otro = UUID.randomUUID();

        assertThatThrownBy(() -> service.assign(new AssignVerificationStandardsCommand(otro, presion.id(), List.of())))
                .isInstanceOf(EquipmentTypeNotFoundException.class);
        verify(standardsPort, never()).save(any());
    }

    @Test
    @DisplayName("una verificacion de otro tipo es 404 de la verificacion, no del tipo")
    void verificacionDeOtroTipo() {
        assertThatThrownBy(() -> service.assign(asignar(UUID.randomUUID())))
                .isInstanceOf(VerificationNotFoundException.class);
        verify(standardsPort, never()).save(any());
    }

    @Test
    @DisplayName("una verificacion retirada no recibe patrones")
    void verificacionRetirada() {
        assertThatThrownBy(() -> service.assign(asignar(retirada.id())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retirada");
        verify(standardsPort, never()).save(any());
    }

    @Test
    @DisplayName("un tipo retirado no recibe patrones")
    void tipoRetirado() {
        when(typePort.findById(idTipo)).thenReturn(Optional.of(tipo(false)));

        assertThatThrownBy(() -> service.assign(asignar(presion.id())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retirado");
        verify(standardsPort, never()).save(any());
    }

    @Test
    @DisplayName("un patron que no existe es 404 con su identificador")
    void patronInexistente() {
        UUID inventado = UUID.randomUUID();
        when(referencePort.findById(inventado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(asignar(presion.id(), inventado)))
                .isInstanceOf(ReferenceStandardNotFoundException.class)
                .hasMessageContaining(inventado.toString());
        verify(standardsPort, never()).save(any());
    }

    @Test
    @DisplayName("un patron retirado no se anade")
    void patronRetiradoNuevo() {
        ReferenceStandard viejo = patron(false);
        when(referencePort.findById(viejo.getId())).thenReturn(Optional.of(viejo));

        assertThatThrownBy(() -> service.assign(asignar(presion.id(), viejo.getId())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retirado");
        verify(standardsPort, never()).save(any());
    }

    @Test
    @DisplayName("un patron retirado que ya estaba puede quedarse al cambiar los demas")
    void patronRetiradoQueYaEstaba() {
        ReferenceStandard viejo = patron(false);
        ReferenceStandard nuevo = patron(true);
        when(standardsPort.findByVerification(presion.id()))
                .thenReturn(VerificationStandards.rehydrate(presion.id(), List.of(viejo.getId())));
        when(referencePort.findById(viejo.getId())).thenReturn(Optional.of(viejo));
        when(referencePort.findById(nuevo.getId())).thenReturn(Optional.of(nuevo));

        VerificationStandards guardados = service.assign(asignar(presion.id(), viejo.getId(), nuevo.getId()));

        assertThat(guardados.getPatrones()).containsExactlyInAnyOrder(viejo.getId(), nuevo.getId());
    }

    @Test
    @DisplayName("listar da una entrada por verificacion vigente, en su orden, y no pregunta por las retiradas")
    void listar() {
        when(standardsPort.findByVerifications(List.of(presion.id())))
                .thenReturn(List.of(VerificationStandards.empty(presion.id())));

        assertThat(service.standardsOf(idTipo)).extracting(VerificationStandards::getIdVerificacion)
                .containsExactly(presion.id());
    }
}
