package com.malphasos.malphasos.client.domain.client;

import com.malphasos.malphasos.client.domain.client.events.ClientCreatedEvent;
import com.malphasos.malphasos.client.domain.client.events.ClientDeactivatedEvent;
import com.malphasos.malphasos.client.domain.client.events.ClientPayload;
import com.malphasos.malphasos.client.domain.client.events.ClientUpdatedEvent;
import com.malphasos.malphasos.client.domain.client.events.LegalRepresentativeAppointedEvent;
import com.malphasos.malphasos.client.domain.client.events.LegalRepresentativePayload;
import com.malphasos.malphasos.client.domain.client.events.LegalRepresentativeRemovedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Organización que contrata el mantenimiento de sus equipos.
 *
 * <p>El agregado abarca al cliente, sus correos y teléfonos —que no existen sin él— y los
 * identificadores de las personas que lo representan legalmente. <b>No abarca sus sedes</b>: cada
 * sede es un agregado propio que apunta aquí por identificador, de modo que cargar un cliente no
 * arrastra su organización entera y dos personas editando sedes distintas no compiten por la misma
 * fila.
 *
 * <p>Los representantes se guardan como identificadores de persona y no como objetos: pertenecen a
 * otro contexto acotado, y traérselos aquí ataría este módulo al modelo de personas.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Client extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    /** NIT o documento. No cambia: es como se identifica al cliente frente al Estado. */
    private final String documento;

    private final IdentificationType tipoIdentificacion;

    private String razonSocial;

    /** Puede faltar: el esquema lo admite nulo. */
    private UUID idPais;

    private boolean estadoActivo;

    private final List<EmailClient> correos;

    private final List<PhoneClient> telefonos;

    /** Identificadores de las personas que representan legalmente al cliente. */
    private final Set<UUID> representantes;

    private Client(
            UUID id,
            String documento,
            IdentificationType tipoIdentificacion,
            String razonSocial,
            UUID idPais,
            boolean estadoActivo,
            List<EmailClient> correos,
            List<PhoneClient> telefonos,
            Set<UUID> representantes) {

        this.id = id;
        this.documento = documento;
        this.tipoIdentificacion = tipoIdentificacion;
        this.razonSocial = razonSocial;
        this.idPais = idPais;
        this.estadoActivo = estadoActivo;
        // Copias propias: el agregado no comparte sus colecciones con quien lo construyo. En el
        // original las listas ni siquiera se inicializaban, de modo que agregar el primer correo
        // lanzaba NullPointerException.
        this.correos = new ArrayList<>(correos);
        this.telefonos = new ArrayList<>(telefonos);
        this.representantes = new LinkedHashSet<>(representantes);
    }

    /** Registra un cliente nuevo. */
    public static Client create(
            String documento, IdentificationType tipoIdentificacion, String razonSocial, UUID idPais) {

        Client cliente = new Client(
                UUID.randomUUID(),
                validarDocumento(documento),
                exigirTipo(tipoIdentificacion),
                validarRazonSocial(razonSocial),
                idPais,
                true,
                List.of(),
                List.of(),
                Set.of());

        cliente.registerEvent(new ClientCreatedEvent(
                cliente.metadataFor(ClientCreatedEvent.TYPE), cliente.payload()));

        return cliente;
    }

    /** Reconstruye un cliente ya existente desde lo persistido, sin registrar eventos. */
    public static Client rehydrate(
            UUID id,
            String documento,
            IdentificationType tipoIdentificacion,
            String razonSocial,
            UUID idPais,
            boolean estadoActivo,
            List<EmailClient> correos,
            List<PhoneClient> telefonos,
            Set<UUID> representantes) {

        return new Client(
                id, documento, tipoIdentificacion, razonSocial, idPais, estadoActivo,
                correos, telefonos, representantes);
    }

    /** Cambia la razón social o el país. Un valor nulo deja el campo como está. */
    public void update(String razonSocial, UUID idPais) {
        boolean cambio = false;

        if (razonSocial != null) {
            String validada = validarRazonSocial(razonSocial);
            if (!validada.equals(this.razonSocial)) {
                this.razonSocial = validada;
                cambio = true;
            }
        }
        if (idPais != null && !idPais.equals(this.idPais)) {
            this.idPais = idPais;
            cambio = true;
        }

        if (cambio) {
            registerEvent(new ClientUpdatedEvent(metadataFor(ClientUpdatedEvent.TYPE), payload()));
        }
    }

    /** Retira el cliente sin borrarlo. Retirar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ClientDeactivatedEvent(
                metadataFor(ClientDeactivatedEvent.TYPE), payload()));
    }

    // ---------------------------------------------------------------------------
    // Representantes legales
    // ---------------------------------------------------------------------------

    /**
     * Nombra representante legal a una persona.
     *
     * <p>Nombrar a quien ya lo es no hace nada. La relación es de muchos a muchos —una persona
     * puede representar a varios clientes— y por eso se guarda un conjunto de identificadores y no
     * un único valor.
     */
    public void appointRepresentative(UUID idPersona) {
        if (idPersona == null) {
            throw new IllegalArgumentException("Un representante legal es una persona");
        }
        if (!representantes.add(idPersona)) {
            return;
        }

        registerEvent(new LegalRepresentativeAppointedEvent(
                metadataFor(LegalRepresentativeAppointedEvent.TYPE),
                new LegalRepresentativePayload(idPersona)));
    }

    /** Retira a una persona de la representación legal. Retirar a quien no lo es no hace nada. */
    public void removeRepresentative(UUID idPersona) {
        if (!representantes.remove(idPersona)) {
            return;
        }

        registerEvent(new LegalRepresentativeRemovedEvent(
                metadataFor(LegalRepresentativeRemovedEvent.TYPE),
                new LegalRepresentativePayload(idPersona)));
    }

    // ---------------------------------------------------------------------------
    // Correos y telefonos
    // ---------------------------------------------------------------------------

    /**
     * Agrega un correo de contacto.
     *
     * <p>No emite evento: es contabilidad interna del cliente y no hay nadie fuera a quien le
     * importe. Los eventos describen hechos que otro contexto podría querer saber.
     */
    public EmailClient addEmail(String correo) {
        EmailClient nuevo = EmailClient.create(correo);
        correos.add(nuevo);

        return nuevo;
    }

    /**
     * Retira un correo, dejándolo inactivo.
     *
     * <p>Falla si no existe. El original lo buscaba y, si no lo encontraba, no hacía nada y
     * devolvía como si hubiera funcionado.
     */
    public void removeEmail(UUID idCorreo) {
        correos.stream()
                .filter(correo -> correo.getId().equals(idCorreo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "El cliente no tiene un correo con el identificador " + idCorreo))
                .deactivate();
    }

    public PhoneClient addPhone(String telefono) {
        PhoneClient nuevo = PhoneClient.create(telefono);
        telefonos.add(nuevo);

        return nuevo;
    }

    /** Retira un teléfono, dejándolo inactivo. Falla si no existe. */
    public void removePhone(UUID idTelefono) {
        telefonos.stream()
                .filter(telefono -> telefono.getId().equals(idTelefono))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "El cliente no tiene un telefono con el identificador " + idTelefono))
                .deactivate();
    }

    /** Los correos y teléfonos se entregan sin posibilidad de alterarlos desde fuera. */
    public List<EmailClient> getCorreos() {
        return List.copyOf(correos);
    }

    public List<PhoneClient> getTelefonos() {
        return List.copyOf(telefonos);
    }

    public Set<UUID> getRepresentantes() {
        return Set.copyOf(representantes);
    }

    @Override
    protected String aggregateType() {
        return "Client";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ClientPayload payload() {
        return new ClientPayload(documento, razonSocial);
    }

    private static String validarDocumento(String documento) {
        if (documento == null || documento.isBlank()) {
            throw new IllegalArgumentException("Un cliente necesita su documento");
        }

        String limpio = documento.trim();
        if (limpio.length() > 11) {
            throw new IllegalArgumentException(
                    "El documento no puede pasar de 11 caracteres, y se recibio: " + limpio);
        }

        return limpio;
    }

    private static IdentificationType exigirTipo(IdentificationType tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("Un cliente necesita su tipo de identificacion");
        }

        return tipo;
    }

    private static String validarRazonSocial(String razonSocial) {
        if (razonSocial == null || razonSocial.isBlank()) {
            throw new IllegalArgumentException("Un cliente necesita razon social");
        }

        return razonSocial.trim();
    }
}
