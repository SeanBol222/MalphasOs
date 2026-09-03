package com.malphasos.malphasos.client.infrastructure.input.mapper;

import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ClientResponse;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ContactResponse;
import com.malphasos.malphasos.client.infrastructure.input.model.response.HeadquarterResponse;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ManagerResponse;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ServiceAreaResponse;
import java.util.List;
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
public class ClientRestMapper {

    public ClientResponse toResponse(Client cliente) {
        return ClientResponse.builder()
                .id(cliente.getId())
                .documento(cliente.getDocumento())
                .tipoIdentificacion(cliente.getTipoIdentificacion())
                .razonSocial(cliente.getRazonSocial())
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
                .representantes(cliente.getRepresentantes())
                .build();
    }

    public List<ClientResponse> toClientResponseList(List<Client> clientes) {
        return clientes.stream().map(this::toResponse).toList();
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

    public ManagerResponse toResponse(Manager encargado) {
        return ManagerResponse.builder()
                .idPersona(encargado.getIdPersona())
                .tipo(encargado.getTipo())
                .idSede(encargado.getIdSede())
                .idAreaServicio(encargado.getIdAreaServicio())
                .estadoActivo(encargado.isEstadoActivo())
                .build();
    }

    public List<ManagerResponse> toManagerResponseList(List<Manager> encargados) {
        return encargados.stream().map(this::toResponse).toList();
    }
}
