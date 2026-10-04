package com.malphasos.malphasos.client.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.application.services.client.ClientService;
import com.malphasos.malphasos.client.application.services.headquarter.HeadquarterService;
import com.malphasos.malphasos.client.application.services.serviceArea.ServiceAreaService;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las seis lecturas de este módulo acotan por dueño, y aquí están las seis juntas.
 *
 * <p>Van en una clase propia y no repartidas entre las tres pruebas de servicio porque <b>no son
 * seis detalles, son una regla</b>: lo que un representante legal puede leer de este módulo son sus
 * clientes y lo que cuelga de ellos. Repartida, la regla se vería como media docena de casos sueltos
 * y nadie notaría que falta uno.
 *
 * <p>Las pruebas de cada servicio siguen cubriendo lo suyo, y todas pasan el alcance libre: eso es
 * cobertura <b>alrededor</b> de este filtro y no sobre él, que es exactamente la forma que tenían los
 * cuatro huecos encontrados mutando en esta misma revisión.
 */
@ExtendWith(MockitoExtension.class)
class OwnershipFilteringTest {

    private static final UUID MIO = UUID.randomUUID();
    private static final UUID AJENO = UUID.randomUUID();

    @Mock private ClientPersistencePort clientPort;
    @Mock private HeadquarterPersistencePort headquarterPort;
    @Mock private ServiceAreaPersistencePort areaPort;
    @Mock private PersonCommunicationPort personCommunicationPort;
    @Mock private CityServicePort cityServicePort;
    @Mock private EventDispatcherPort dispatcher;

    /** Alcance de un representante que solo representa al cliente {@code MIO}. */
    private static final ReadScope SOLO_MIO = ReadScope.ofClients(Set.of(MIO));

    private ClientService clientes() {
        return new ClientService(clientPort, personCommunicationPort, dispatcher);
    }

    private HeadquarterService sedes() {
        return new HeadquarterService(headquarterPort, clientPort, cityServicePort, dispatcher);
    }

    private ServiceAreaService areas() {
        return new ServiceAreaService(areaPort, headquarterPort, dispatcher);
    }

    private Client unCliente(UUID id) {
        return Client.rehydrate(id, "900" + id.hashCode(), IdentificationType.NIT_JURIDICO,
                "Hospital", null, true, List.of(), List.of(), Set.of());
    }

    private Headquarter unaSede(UUID id, UUID idCliente) {
        return Headquarter.rehydrate(
                id, "Sede", new Address("Calle", "Carrera", "1"), idCliente, UUID.randomUUID(), true);
    }

    // ---------------------------------------------------------------- clientes

    @Test
    @DisplayName("el listado de clientes trae solo los del alcance, no la lista entera")
    void listarClientesAcotado() {
        when(clientPort.findAllByIds(Set.of(MIO))).thenReturn(List.of(unCliente(MIO)));

        assertThat(clientes().findAll(SOLO_MIO)).extracting(Client::getId).containsExactly(MIO);
        // Y no se pide la lista completa: si se pidiera y se filtrara despues, la base seguiria
        // leyendo todos los clientes del sistema en cada peticion.
        verify(clientPort, never()).findAll();
    }

    @Test
    @DisplayName("sin restriccion el listado no acota y no pasa por la consulta filtrada")
    void listarClientesLibre() {
        when(clientPort.findAll()).thenReturn(List.of(unCliente(MIO), unCliente(AJENO)));

        assertThat(clientes().findAll(ReadScope.unrestricted())).hasSize(2);
        verify(clientPort, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("un cliente ajeno se anuncia como inexistente, y sin llegar a consultarlo")
    void clienteAjenoNoExiste() {
        assertThatThrownBy(() -> clientes().findById(AJENO, SOLO_MIO))
                .isInstanceOf(ClientNotFoundException.class);

        // Es la diferencia entre «no es tuyo» y «no existe»: no se consulta, de modo que la
        // respuesta es identica a la de un identificador inventado y no confirma que esa fila este.
        verify(clientPort, never()).findById(any());
    }

    // ------------------------------------------------------------------- sedes

    @Test
    @DisplayName("las sedes de un cliente ajeno se anuncian como cliente inexistente")
    void sedesDeClienteAjeno() {
        // El cliente ajeno EXISTE, y esto no es decoracion: sin estubarlo, al desactivar el filtro
        // la comprobacion de existencia lanzaba el mismo error -Mockito devuelve Optional.empty()
        // para un metodo sin estubar- y la prueba pasaba sin ejercer el alcance. Lo destapo una
        // mutacion que sobrevivio. Va con lenient() porque, con el filtro puesto, nunca se usa: es
        // la declaracion de que esta aqui para que no sea esto lo que falle.
        lenient().when(clientPort.findById(AJENO)).thenReturn(Optional.of(unCliente(AJENO)));

        assertThatThrownBy(() -> sedes().findByClient(AJENO, SOLO_MIO))
                .isInstanceOf(ClientNotFoundException.class);

        verify(headquarterPort, never()).findByClient(any());
    }

    @Test
    @DisplayName("las sedes del cliente propio se devuelven")
    void sedesDelClientePropio() {
        UUID sede = UUID.randomUUID();
        when(clientPort.findById(MIO)).thenReturn(Optional.of(unCliente(MIO)));
        when(headquarterPort.findByClient(MIO)).thenReturn(List.of(unaSede(sede, MIO)));

        assertThat(sedes().findByClient(MIO, SOLO_MIO)).extracting(Headquarter::getId).containsExactly(sede);
    }

    @Test
    @DisplayName("una sede de otro cliente no existe para quien pregunta")
    void sedeDeOtroCliente() {
        UUID sede = UUID.randomUUID();
        when(headquarterPort.findById(sede)).thenReturn(Optional.of(unaSede(sede, AJENO)));

        // Aqui hay que leer la fila para saber de quien es: el dueno no esta en la ruta. Lo que no
        // puede pasar es que se devuelva.
        assertThatThrownBy(() -> sedes().findById(sede, SOLO_MIO))
                .isInstanceOf(HeadquarterNotFoundException.class);
    }

    @Test
    @DisplayName("la propia sede se devuelve")
    void sedePropia() {
        UUID sede = UUID.randomUUID();
        when(headquarterPort.findById(sede)).thenReturn(Optional.of(unaSede(sede, MIO)));

        assertThat(sedes().findById(sede, SOLO_MIO).getId()).isEqualTo(sede);
    }

    // ------------------------------------------------------------------- areas

    @Test
    @DisplayName("las areas de una sede ajena se anuncian como sede inexistente")
    void areasDeSedeAjena() {
        UUID sede = UUID.randomUUID();
        when(headquarterPort.findById(sede)).thenReturn(Optional.of(unaSede(sede, AJENO)));

        assertThatThrownBy(() -> areas().findByHeadquarter(sede, SOLO_MIO))
                .isInstanceOf(HeadquarterNotFoundException.class);

        verify(areaPort, never()).findByHeadquarter(any());
    }

    @Test
    @DisplayName("un area de otro cliente no existe para quien pregunta, a dos saltos de distancia")
    void areaDeOtroCliente() {
        UUID sede = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        when(areaPort.findById(area)).thenReturn(Optional.of(ServiceArea.rehydrate(area, "UCI", sede, true)));
        when(headquarterPort.findById(sede)).thenReturn(Optional.of(unaSede(sede, AJENO)));

        // El dueno de un area esta a dos saltos: area -> sede -> cliente. Es el unico de los seis
        // casos que cuesta una consulta extra, y es la razon de que solo se pague cuando se filtra.
        assertThatThrownBy(() -> areas().findById(area, SOLO_MIO))
                .isInstanceOf(ServiceAreaNotFoundException.class);
    }

    @Test
    @DisplayName("sin restriccion no se paga la consulta que resuelve el dueno de un area")
    void areaSinRestriccionNoCuestaLaConsultaExtra() {
        UUID sede = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        when(areaPort.findById(area)).thenReturn(Optional.of(ServiceArea.rehydrate(area, "UCI", sede, true)));
        // La sede se estuba a proposito, y con lenient() porque este camino no deberia pedirla: sin
        // el estubado, quitar el atajo hacia lanzar a requireHeadquarter y la prueba fallaba por eso
        // en vez de por la verificacion que dice comprobar. Lo destapo mutar el atajo.
        lenient().when(headquarterPort.findById(sede)).thenReturn(Optional.of(unaSede(sede, MIO)));

        assertThat(areas().findById(area, ReadScope.unrestricted()).getId()).isEqualTo(area);

        // A la gente de la casa no se le cobra el filtro que no se le aplica. Si esta verificacion
        // cae, cada lectura de un area pasa a costar dos consultas para todo el mundo.
        verify(headquarterPort, never()).findById(any());
    }

    @Test
    @DisplayName("el area propia se devuelve")
    void areaPropia() {
        UUID sede = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        when(areaPort.findById(area)).thenReturn(Optional.of(ServiceArea.rehydrate(area, "UCI", sede, true)));
        when(headquarterPort.findById(sede)).thenReturn(Optional.of(unaSede(sede, MIO)));

        assertThat(areas().findById(area, SOLO_MIO).getId()).isEqualTo(area);
    }

    @Test
    @DisplayName("un representante sin clientes no lee nada de este modulo")
    void sinClientesNoLeeNada() {
        ReadScope nada = ReadScope.ofClients(Set.of());
        when(clientPort.findAllByIds(Set.of())).thenReturn(List.of());

        assertThat(clientes().findAll(nada)).isEmpty();
        assertThatThrownBy(() -> clientes().findById(MIO, nada)).isInstanceOf(ClientNotFoundException.class);
    }
}
