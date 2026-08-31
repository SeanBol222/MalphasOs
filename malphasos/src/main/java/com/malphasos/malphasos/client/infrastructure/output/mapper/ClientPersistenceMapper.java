package com.malphasos.malphasos.client.infrastructure.output.mapper;

import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.EmailClient;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.client.PhoneClient;
import com.malphasos.malphasos.client.infrastructure.output.entities.ClientEntity;
import com.malphasos.malphasos.client.infrastructure.output.entities.EmailClientEntity;
import com.malphasos.malphasos.client.infrastructure.output.entities.LegalRepresentativeEntity;
import com.malphasos.malphasos.client.infrastructure.output.entities.PhoneClientEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el agregado y sus filas.
 *
 * <p>A mano, no con MapStruct: el agregado se construye por {@code rehydrate} y no ofrece setters ni
 * builder a propósito. Aquí además hay reglas que ningún generador podría adivinar —qué hacer con
 * un representante retirado, cómo casar filas existentes con el estado del agregado—, y por eso el
 * mapeo hacia la entidad recibe la fila actual en vez de construir una nueva.
 */
@Component
public class ClientPersistenceMapper {

    public Client toDomain(ClientEntity entity) {
        List<EmailClient> correos = entity.getCorreos().stream()
                .map(correo -> EmailClient.rehydrate(
                        correo.getId(), correo.getCorreo(), correo.isEstadoActivo()))
                .toList();

        List<PhoneClient> telefonos = entity.getTelefonos().stream()
                .map(telefono -> PhoneClient.rehydrate(
                        telefono.getId(), telefono.getTelefono(), telefono.isEstadoActivo()))
                .toList();

        // Solo los representantes activos: el agregado responde "quien representa hoy a este
        // cliente", y los retirados quedan en la tabla como historial.
        Set<UUID> representantes = entity.getRepresentantes().stream()
                .filter(LegalRepresentativeEntity::isEstadoActivo)
                .map(LegalRepresentativeEntity::getPersona)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        return Client.rehydrate(
                entity.getId(),
                entity.getDocumento(),
                IdentificationType.desdeEsquema(entity.getTipoIdentificacion()),
                entity.getRazonSocial(),
                entity.getIdPais(),
                entity.isEstadoActivo(),
                correos,
                telefonos,
                representantes);
    }

    public List<Client> toDomainList(List<ClientEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    /**
     * Vuelca el agregado sobre su fila.
     *
     * <p>Recibe la entidad existente cuando la hay, para conservar las filas hijas que JPA ya
     * gestiona y limitarse a poner al día su contenido. Construir una entidad nueva en cada guardado
     * haría que Hibernate insertara duplicados de los contactos.
     */
    public ClientEntity toEntity(Client cliente, ClientEntity existente) {
        ClientEntity entity = existente != null ? existente : new ClientEntity();

        entity.setId(cliente.getId());
        entity.setDocumento(cliente.getDocumento());
        entity.setTipoIdentificacion(cliente.getTipoIdentificacion().valorEnEsquema());
        entity.setRazonSocial(cliente.getRazonSocial());
        entity.setIdPais(cliente.getIdPais());
        entity.setEstadoActivo(cliente.isEstadoActivo());

        sincronizarCorreos(cliente, entity);
        sincronizarTelefonos(cliente, entity);
        sincronizarRepresentantes(cliente, entity);

        return entity;
    }

    private void sincronizarCorreos(Client cliente, ClientEntity entity) {
        for (EmailClient correo : cliente.getCorreos()) {
            EmailClientEntity fila = entity.getCorreos().stream()
                    .filter(existente -> existente.getId().equals(correo.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        EmailClientEntity nueva = new EmailClientEntity();
                        nueva.setId(correo.getId());
                        nueva.setCorreo(correo.getCorreo());
                        nueva.setCliente(entity);
                        entity.getCorreos().add(nueva);

                        return nueva;
                    });

            fila.setEstadoActivo(correo.isEstadoActivo());
        }
    }

    private void sincronizarTelefonos(Client cliente, ClientEntity entity) {
        for (PhoneClient telefono : cliente.getTelefonos()) {
            PhoneClientEntity fila = entity.getTelefonos().stream()
                    .filter(existente -> existente.getId().equals(telefono.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        PhoneClientEntity nueva = new PhoneClientEntity();
                        nueva.setId(telefono.getId());
                        nueva.setTelefono(telefono.getTelefono());
                        nueva.setCliente(entity);
                        entity.getTelefonos().add(nueva);

                        return nueva;
                    });

            fila.setEstadoActivo(telefono.isEstadoActivo());
        }
    }

    /**
     * Los representantes son el único caso donde la fila puede tener que cambiar de estado sin que
     * el agregado lo diga: quien ya no está en el conjunto es que fue retirado, y su fila queda
     * inactiva en vez de desaparecer. Quien vuelve a estarlo reactiva la suya.
     */
    private void sincronizarRepresentantes(Client cliente, ClientEntity entity) {
        Set<UUID> activos = cliente.getRepresentantes();

        for (LegalRepresentativeEntity fila : entity.getRepresentantes()) {
            fila.setEstadoActivo(activos.contains(fila.getPersona()));
        }

        for (UUID persona : activos) {
            boolean yaEsta = entity.getRepresentantes().stream()
                    .anyMatch(fila -> fila.getPersona().equals(persona));

            if (!yaEsta) {
                LegalRepresentativeEntity nueva = new LegalRepresentativeEntity();
                nueva.setPersona(persona);
                nueva.setCliente(entity);
                nueva.setEstadoActivo(true);
                entity.getRepresentantes().add(nueva);
            }
        }
    }
}
