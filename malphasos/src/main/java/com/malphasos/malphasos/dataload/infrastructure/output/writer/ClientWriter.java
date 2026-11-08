package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static com.malphasos.malphasos.dataload.infrastructure.output.writer.Steps.paso;

import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.ClientAcronym;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.services.Names;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.location.application.ports.output.CityPersistencePort;
import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.country.Country;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Escribe el cliente de un archivo con sus correos, teléfonos y supervisores, sus sedes, sus áreas y sus
 * equipos, cada uno por el {@code create} —o el {@code register}— de su agregado.
 *
 * <p>Lo que ya existe se reconoce como en la validación: el cliente por su documento, la sede por su
 * nombre dentro de su cliente, el área dentro de su sede y el equipo por su modelo y su serie. Un cliente
 * que ya está no se toca —ni sus correos ni sus supervisores—, pero sus sedes y áreas nuevas entran.
 *
 * <p>La sigla, si no viene, se genera de la razón social y se desempata contra las que hay, como en la
 * pantalla, con el mismo candado. Un equipo sin serie recibe la provisional del agregado, y uno
 * {@code retirado} entra dado de baja.
 */
@Component
@RequiredArgsConstructor
class ClientWriter {

    private final ClientPersistencePort clientes;
    private final HeadquarterPersistencePort sedes;
    private final ServiceAreaPersistencePort areas;
    private final ClientEquipmentPersistencePort equipos;
    private final CountryPersistencePort paises;
    private final CityPersistencePort ciudades;

    void write(ReadFile archivo, LoadRefs refs) {
        String f = archivo.nombre();
        LoadDocument documento = archivo.documento();

        for (int c = 0; c < documento.clientes().size(); c++) {
            LoadDocument.Client cliente = documento.clientes().get(c);
            String ruta = "/clientes/" + c;
            Optional<Client> yaEsta = clientes.findByDocumento(cliente.documento().trim()).filter(Client::isEstadoActivo);
            UUID idCliente = yaEsta.map(Client::getId)
                    .orElseGet(() -> paso(f, ruta, () -> clientes.save(nuevoCliente(cliente, refs, f, ruta)).getId()));
            refs.put(cliente.ref(), idCliente);

            Map<String, UUID> sedesQueEstan = new HashMap<>();
            sedes.findByClient(idCliente).stream().filter(Headquarter::isEstadoActivo)
                    .forEach(s -> sedesQueEstan.putIfAbsent(Names.plano(s.getNombre()), s.getId()));
            for (int s = 0; s < cliente.sedes().size(); s++) {
                LoadDocument.Site sede = cliente.sedes().get(s);
                String rutaSede = ruta + "/sedes/" + s;
                UUID idSede = sedesQueEstan.computeIfAbsent(Names.plano(sede.nombre()), k -> paso(f, rutaSede,
                        () -> sedes.save(Headquarter.create(sede.nombre(), new Address(sede.calle(), sede.carrera(),
                                sede.numero()), idCliente, ciudad(sede.ciudad()))).getId()));
                refs.put(sede.ref(), idSede);

                Map<String, UUID> areasQueEstan = new HashMap<>();
                areas.findByHeadquarter(idSede).stream().filter(ServiceArea::isEstadoActivo)
                        .forEach(a -> areasQueEstan.putIfAbsent(Names.plano(a.getNombre()), a.getId()));
                for (int a = 0; a < sede.areas().size(); a++) {
                    LoadDocument.Area area = sede.areas().get(a);
                    int n = a;
                    refs.put(area.ref(), areasQueEstan.computeIfAbsent(Names.plano(area.nombre()), k ->
                            paso(f, rutaSede + "/areas/", n, () -> areas.save(ServiceArea.create(area.nombre(), idSede)).getId())));
                }
            }
        }

        Map<String, UUID> equiposQueEstan = new HashMap<>();
        equipos.findAll().stream().filter(ClientEquipment::isEstadoActivo).filter(e -> !e.isSerieProvisional())
                .forEach(e -> equiposQueEstan.putIfAbsent(e.getIdModelo() + "|" + Names.plano(e.getSerie()), e.getId()));
        for (int i = 0; i < documento.equipos().size(); i++) {
            LoadDocument.Equipment equipo = documento.equipos().get(i);
            UUID idModelo = refs.id(equipo.modelo());
            UUID yaEsta = equipo.serie() == null ? null : equiposQueEstan.get(idModelo + "|" + Names.plano(equipo.serie()));
            int n = i;
            refs.put(equipo.ref(), yaEsta != null ? yaEsta : paso(f, "/equipos/", n, () -> nuevoEquipo(equipo, idModelo, refs).getId()));
        }
    }

    private Client nuevoCliente(LoadDocument.Client cliente, LoadRefs refs, String archivo, String ruta) {
        clientes.lockAcronymAllocation();
        String sigla;
        if (cliente.sigla() != null) {
            sigla = ClientAcronym.validar(cliente.sigla());
            if (clientes.existsBySigla(sigla)) {
                throw Steps.rechazo(archivo, ruta + "/sigla", "La sigla «" + sigla + "» ya es de otro cliente de MalphasOS:"
                        + " encabeza el número de sus hojas de vida y no se repite. Se cambia en el archivo, o se omite"
                        + " para que MalphasOS la genere");
            }
        } else {
            String base = ClientAcronym.base(cliente.razonSocial());
            int numero = 1;
            while (clientes.existsBySigla(ClientAcronym.conDesempate(base, numero))) {
                numero++;
            }
            sigla = ClientAcronym.conDesempate(base, numero);
        }

        Client nuevo = Client.create(cliente.documento().trim(), tipo(cliente.tipoIdentificacion()), cliente.razonSocial(),
                pais(cliente.pais()), sigla);
        cliente.correos().forEach(nuevo::addEmail);
        cliente.telefonos().forEach(nuevo::addPhone);
        cliente.supervisores().forEach(s -> nuevo.appointSupervisor(refs.id(s.persona()), s.cargo()));
        return nuevo;
    }

    private ClientEquipment nuevoEquipo(LoadDocument.Equipment equipo, UUID idModelo, LoadRefs refs) {
        ClientEquipment nuevo = ClientEquipment.register(equipo.serie(), idModelo, refs.id(equipo.area()),
                equipo.numeroInventario(), equipo.fechaCompra(), equipo.valorCompra(), equipo.codigoInterno(),
                equipo.proveedor());
        if (Boolean.TRUE.equals(equipo.retirado())) {
            nuevo.decommission();
        }
        return equipos.save(nuevo);
    }

    private static IdentificationType tipo(String valor) {
        for (IdentificationType tipo : IdentificationType.values()) {
            if (tipo.valorEnEsquema().equals(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de identificación desconocido: " + valor);
    }

    private UUID pais(String codigoIso) {
        return paises.findAll().stream().filter(Country::isEstadoActivo).filter(p -> p.getCodigoIso().equals(codigoIso))
                .findFirst().map(Country::getId)
                .orElseThrow(() -> new IllegalArgumentException("El país «" + codigoIso + "» no está en el catálogo"));
    }

    /** La validación garantiza que casa con una sola, sin tildes ni mayúsculas. */
    private UUID ciudad(LoadDocument.City ciudad) {
        List<City> casan = ciudades.findByCountry(pais(ciudad.pais())).stream().filter(City::isEstadoActivo)
                .filter(c -> Names.plano(c.getNombre()).equals(Names.plano(ciudad.nombre()))).toList();
        if (casan.size() != 1) {
            throw new IllegalArgumentException("La ciudad «" + ciudad.nombre() + "» casa con " + casan.size() + " del catálogo");
        }
        return casan.getFirst().getId();
    }
}
