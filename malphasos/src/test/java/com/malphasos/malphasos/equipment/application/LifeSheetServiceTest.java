package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.BrandServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.InterventionServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ManufacturerServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.services.lifeSheet.LifeSheetService;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.location.application.ports.input.CountryServicePort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Dónde se comprueba el alcance al compilar una hoja de vida, y que se comprueba <b>antes de leer
 * nada</b>.
 *
 * <p><b>Esta clase existe porque una mutación sobrevivió.</b> La prueba de integración ya comprobaba
 * que la hoja de vida de un equipo ajeno no se devuelve, y pasaba igual con el filtro quitado del
 * principio: la cuarta sección —el historial— también acota, de modo que el rechazo llegaba al final
 * de todos modos. El resultado para quien llama era el mismo, pero por el camino se habían leído el
 * cliente, la sede y el área <b>de otro cliente</b>. Lo que esta prueba fija no es la respuesta, es
 * dónde está la guarda.
 *
 * <p>Es la tercera vez en esta revisión que aparece la misma forma: una prueba que pasa porque algo
 * <b>distinto</b> de la regla rechaza. La defensa es siempre la misma, y aquí toma la forma de
 * {@code verifyNoInteractions}: comprobar que lo demás <b>no se llegó a tocar</b>.
 */
@ExtendWith(MockitoExtension.class)
class LifeSheetServiceTest {

    private static final UUID EQUIPO = UUID.randomUUID();

    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;
    @Mock private ModelServicePort modelServicePort;
    @Mock private EquipmentServicePort equipmentServicePort;
    @Mock private EquipmentTypeServicePort equipmentTypeServicePort;
    @Mock private BrandServicePort brandServicePort;
    @Mock private ManufacturerServicePort manufacturerServicePort;
    @Mock private InterventionServicePort interventionServicePort;
    @Mock private ServiceAreaServicePort serviceAreaServicePort;
    @Mock private HeadquarterServicePort headquarterServicePort;
    @Mock private ClientServicePort clientServicePort;
    @Mock private CityServicePort cityServicePort;
    @Mock private CountryServicePort countryServicePort;

    @InjectMocks private LifeSheetService service;

    @Test
    @DisplayName("un equipo ajeno se rechaza antes de leer un solo dato del otro cliente")
    void seRechazaAntesDeLeerNada() {
        ReadScope deOtro = ReadScope.ofClients(Set.of(UUID.randomUUID()));
        when(clientEquipmentServicePort.findById(EQUIPO, deOtro))
                .thenThrow(new ClientEquipmentNotFoundException(EQUIPO));

        assertThatThrownBy(() -> service.findByEquipment(EQUIPO, deOtro))
                .isInstanceOf(ClientEquipmentNotFoundException.class);

        // Ninguno de los once saltos siguientes se da. Si la guarda se moviera al final, la
        // respuesta seria la misma y esto fallaria, que es exactamente lo que hay que notar.
        verifyNoInteractions(
                modelServicePort,
                equipmentServicePort,
                equipmentTypeServicePort,
                brandServicePort,
                manufacturerServicePort,
                interventionServicePort,
                serviceAreaServicePort,
                headquarterServicePort,
                clientServicePort,
                cityServicePort,
                countryServicePort);
    }

    @Test
    @DisplayName("el alcance se comprueba una vez y los demas saltos van sin restriccion")
    void elAlcanceSeComprubaUnaVez() {
        // Volver a comprobarlo en cada salto serian once comprobaciones de lo mismo: si quien
        // pregunta tiene derecho al equipo, lo tiene a lo que ese equipo cuelga. Lo que NO puede
        // pasar es que el primero vaya sin restriccion, y eso lo cubre la prueba de arriba.
        ReadScope deOtro = ReadScope.ofClients(Set.of(UUID.randomUUID()));
        when(clientEquipmentServicePort.findById(EQUIPO, deOtro))
                .thenThrow(new ClientEquipmentNotFoundException(EQUIPO));

        assertThatThrownBy(() -> service.findByEquipment(EQUIPO, deOtro))
                .isInstanceOf(ClientEquipmentNotFoundException.class);

        verify(clientEquipmentServicePort).findById(EQUIPO, deOtro);
    }
}
