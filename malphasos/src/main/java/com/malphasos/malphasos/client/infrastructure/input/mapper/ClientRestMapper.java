package com.malphasos.malphasos.client.infrastructure.input.mapper;

import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ClientResponse;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ContactResponse;
import com.malphasos.malphasos.client.infrastructure.input.model.response.HeadquarterResponse;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ServiceAreaResponse;
import com.malphasos.malphasos.client.domain.client.ClientSupervisor;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Traduce los agregados a la respuesta del API.
 *
 * <p>Solo en esa dirección: hacia dentro el controlador construye el comando a mano, porque los
 * identificadores vienen de la ruta y no del cuerpo.
 *
 * <p>Escrito a mano y no con MapStruct, a diferencia del de ubicaciones, porque aquí ninguna de las
 * cuatro traducciones es campo a campo: los contactos son dos tipos distintos que colapsan en uno,
 * la dirección se descompone, y el encargado deriva sus dos columnas de una sola asignación.
 */
@Component
@RequiredArgsConstructor
public class ClientRestMapper {

    /**
     * Los nombres de los supervisores (2026-11-02): quien lee un cliente —su propio supervisor— no tiene
     * {@code person.read}, y sin ellos la pantalla no podía nombrar a nadie.
     */
    private final PersonCommunicationPort personCommunicationPort;

    public ClientResponse toResponse(Client cliente) {
        return toResponse(cliente, nombresDe(List.of(cliente)));
    }

    private ClientResponse toResponse(Client cliente, Map<UUID, String> nombres) {
        return ClientResponse.builder()
                .id(cliente.getId())
                .documento(cliente.getDocumento())
                .tipoIdentificacion(cliente.getTipoIdentificacion())
                .razonSocial(cliente.getRazonSocial())
                .sigla(cliente.getSigla())
                .idPais(cliente.getIdPais())
                .estadoActivo(cliente.isEstadoActivo())
                .correos(cliente.getCorreos().stream()
                        .map(correo -> new ContactResponse(
                                correo.getId(), correo.getCorreo(), correo.isEstadoActivo()))
                        .toList())
                .telefonos(cliente.getTelefonos().stream()
                        .map(telefono -> new ContactResponse(
                                telefono.getId(), telefono.getTelefono(), telefono.isEstadoActivo()))
                        .toList())
                .supervisores(cliente.getSupervisores().stream()
                        .map(supervisor -> new ClientResponse.SupervisorResponse(
                                supervisor.idPersona(), nombres.get(supervisor.idPersona()), supervisor.cargo()))
                        .toList())
                .build();
    }

    /** Los nombres de todos los supervisores en una consulta, y no una por cliente. */
    public List<ClientResponse> toClientResponseList(List<Client> clientes) {
        Map<UUID, String> nombres = nombresDe(clientes);

        return clientes.stream().map(cliente -> toResponse(cliente, nombres)).toList();
    }

    private Map<UUID, String> nombresDe(List<Client> clientes) {
        return personCommunicationPort.namesOf(clientes.stream()
                .flatMap(cliente -> cliente.getSupervisores().stream())
                .map(ClientSupervisor::idPersona)
                .toList());
    }

    public HeadquarterResponse toResponse(Headquarter sede) {
        return HeadquarterResponse.builder()
                .id(sede.getId())
                .nombre(sede.getNombre())
                .calle(sede.getDireccion().calle())
                .carrera(sede.getDireccion().carrera())
                .numero(sede.getDireccion().numero())
                .idCliente(sede.getIdCliente())
                .idCiudad(sede.getIdCiudad())
                .estadoActivo(sede.isEstadoActivo())
                .build();
    }

    public List<HeadquarterResponse> toHeadquarterResponseList(List<Headquarter> sedes) {
        return sedes.stream().map(this::toResponse).toList();
    }

    public ServiceAreaResponse toResponse(ServiceArea area) {
        return new ServiceAreaResponse(
                area.getId(), area.getNombre(), area.getIdSede(), area.isEstadoActivo());
    }

    public List<ServiceAreaResponse> toServiceAreaResponseList(List<ServiceArea> areas) {
        return areas.stream().map(this::toResponse).toList();
    }
}
