package com.malphasos.malphasos.equipment.application.services.lifeSheet;

import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaResponsiblePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.EmailClient;
import com.malphasos.malphasos.client.domain.client.PhoneClient;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.ports.input.BrandServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.InterventionServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ManufacturerServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.location.application.ports.input.CountryServicePort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compila la hoja de vida de un equipo leyendo los agregados en los que sus datos ya viven.
 *
 * <p><b>Por qué no hay una consulta que lo traiga todo de una vez.</b> Sería una unión de nueve
 * tablas, y seis de ellas son de otros dos módulos —{@code client} y {@code location}—. Este proyecto
 * ya tomó esa decisión hoy, al acotar el inventario por dueño: cruzar las tablas ajenas en SQL propio
 * es como dos módulos acaban siendo uno. Aquí se paga el precio opuesto, que son <b>once consultas
 * para armar un documento</b>, y se paga a sabiendas: una hoja de vida se abre para leerla o
 * imprimirla, no en un bucle.
 *
 * <p>Si algún día duele, lo que hay que construir es una vista materializada o un modelo de lectura
 * propio con su tabla, no una consulta que atraviese las fronteras.
 *
 * <p><b>El alcance se comprueba una vez, al principio.</b> Resolver el equipo con el alcance ya
 * responde «no existe» si es de otro cliente, y a partir de ahí el resto se lee sin restricción: ya
 * se sabe que quien pregunta tiene derecho a este equipo, y volver a comprobarlo en cada salto serían
 * once comprobaciones de lo mismo.
 */
@Service
@RequiredArgsConstructor
public class LifeSheetService implements LifeSheetServicePort {

    private final ClientEquipmentServicePort clientEquipmentServicePort;
    private final ModelServicePort modelServicePort;
    private final EquipmentServicePort equipmentServicePort;
    private final EquipmentTypeServicePort equipmentTypeServicePort;
    private final BrandServicePort brandServicePort;
    private final ManufacturerServicePort manufacturerServicePort;
    private final InterventionServicePort interventionServicePort;
    private final ServiceAreaServicePort serviceAreaServicePort;
    private final HeadquarterServicePort headquarterServicePort;
    private final ClientServicePort clientServicePort;
    private final CityServicePort cityServicePort;
    private final CountryServicePort countryServicePort;
    private final ServiceAreaResponsiblePort serviceAreaResponsiblePort;

    @Override
    @Transactional(readOnly = true)
    public LifeSheet findByEquipment(UUID idEquipoCliente, ReadScope alcance) {
        ClientEquipment unidad = clientEquipmentServicePort.findById(idEquipoCliente, alcance);

        Model modelo = modelServicePort.findById(unidad.getIdModelo());
        Equipment delCatalogo = equipmentServicePort.findById(modelo.getIdEquipo());
        EquipmentType tipo = equipmentTypeServicePort.findById(delCatalogo.getIdTipoEquipo());
        Manufacturer fabricante = manufacturerServicePort.findById(modelo.getIdFabricante());

        ServiceArea area = serviceAreaServicePort.findById(unidad.getIdAreaServicio(), ReadScope.unrestricted());
        Headquarter sede = headquarterServicePort.findById(area.getIdSede(), ReadScope.unrestricted());
        Client cliente = clientServicePort.findById(sede.getIdCliente(), ReadScope.unrestricted());

        return new LifeSheet(
                new LifeSheet.Identificacion(
                        unidad.getId(),
                        unidad.getSerie(),
                        unidad.getNumeroInventario(),
                        unidad.getCodigoInterno(),
                        unidad.getProveedor(),
                        unidad.getFechaCompra(),
                        unidad.getValorCompra(),
                        cliente.getRazonSocial(),
                        cliente.getDocumento(),
                        sede.getNombre(),
                        comoTexto(sede.getDireccion()),
                        cityServicePort.findById(sede.getIdCiudad()).getNombre(),
                        area.getNombre(),
                        unidad.isEstadoActivo(),
                        serviceAreaResponsiblePort.responsiblesFor(area.getId()),
                        cliente.getTelefonos().stream()
                                .filter(PhoneClient::isEstadoActivo)
                                .map(PhoneClient::getTelefono)
                                .toList(),
                        cliente.getCorreos().stream()
                                .filter(EmailClient::isEstadoActivo)
                                .map(EmailClient::getCorreo)
                                .toList()),
                new LifeSheet.Tecnica(
                        tipo.getNombre(),
                        tipo.getDefinicionTecnica(),
                        tipo.getTecnologiaPredominante(),
                        tipo.getRecomendacionesCuidado(),
                        tipo.getUso(),
                        tipo.getLimpiezaCotidiana(),
                        brandServicePort.findById(delCatalogo.getIdMarca()).getNombre(),
                        modelo.getNombre(),
                        modelo.getInvima(),
                        // Voltaje y amperaje salen de aqui desde V15, y ya no del tipo.
                        modelo.getFichaTecnica()),
                new LifeSheet.Fabricante(
                        fabricante.getNombre(), paisDe(fabricante)),
                // La cuarta seccion, y la unica que sale de los reportes. Se pide con el alcance
                // original aunque ya se sepa que el equipo es visible: asi esta llamada sigue siendo
                // correcta si algun dia se usa desde otro sitio.
                interventionServicePort.findByEquipment(idEquipoCliente, alcance));
    }

    /**
     * El país del fabricante, que es opcional en el esquema.
     *
     * <p>Se distingue «no tiene país» de «no se pudo consultar» devolviendo {@code null} solo en el
     * primer caso: si el identificador está y el país no existe, el puerto lanza y la hoja de vida no
     * se arma a medias.
     */
    private String paisDe(Manufacturer fabricante) {
        return fabricante.getIdPais() == null
                ? null
                : countryServicePort.findById(fabricante.getIdPais()).getNombre();
    }

    /** La dirección de la sede, que el dominio guarda en tres piezas. */
    private static String comoTexto(Address direccion) {
        if (direccion == null) {
            return null;
        }

        return "Calle " + direccion.calle() + " # " + direccion.carrera() + " - " + direccion.numero();
    }
}
