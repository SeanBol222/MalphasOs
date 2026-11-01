package com.malphasos.malphasos.client.domain.client;

import com.malphasos.malphasos.client.domain.client.events.ClientCreatedEvent;
import com.malphasos.malphasos.client.domain.client.events.ClientDeactivatedEvent;
import com.malphasos.malphasos.client.domain.client.events.ClientPayload;
import com.malphasos.malphasos.client.domain.client.events.ClientUpdatedEvent;
import com.malphasos.malphasos.client.domain.client.events.SupervisorAppointedEvent;
import com.malphasos.malphasos.client.domain.client.events.SupervisorPayload;
import com.malphasos.malphasos.client.domain.client.events.SupervisorRemovedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Organización que contrata el mantenimiento de sus equipos.
 *
 * <p>El agregado abarca al cliente, sus correos y teléfonos —que no existen sin él— y los
 * personas que lo supervisan. <b>No abarca sus sedes</b>: cada
 * sede es un agregado propio que apunta aquí por identificador, de modo que cargar un cliente no
 * arrastra su organización entera y dos personas editando sedes distintas no compiten por la misma
 * fila.
 *
 * <p>Los supervisores se guardan como identificadores de persona y no como objetos: pertenecen a
 * otro contexto acotado, y traérselos aquí ataría este módulo al modelo de personas. Hasta V53 eran los
 * representantes legales, que solo daban cuenta al cliente; ver {@link ClientSupervisor}.
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

    /**
     * Encabeza el número de las hojas de vida: «HV-CDN-0001». Desde {@code V19}. Se genera de la razón
     * social al crear el cliente y no la sigue después; ver {@link ClientAcronym}.
     */
    private String sigla;

    /** Puede faltar: el esquema lo admite nulo. */
    private UUID idPais;

    private boolean estadoActivo;

    private final List<EmailClient> correos;

    private final List<PhoneClient> telefonos;

    /** Quienes lo supervisan hoy, por persona, en el orden en que se nombraron. */
    private final Map<UUID, ClientSupervisor> supervisores;

    private Client(
            UUID id,
            String documento,
            IdentificationType tipoIdentificacion,
            String razonSocial,
            String sigla,
            UUID idPais,
            boolean estadoActivo,
            List<EmailClient> correos,
            List<PhoneClient> telefonos,
            List<ClientSupervisor> supervisores) {

        this.id = id;
        this.documento = documento;
        this.tipoIdentificacion = tipoIdentificacion;
        this.razonSocial = razonSocial;
        this.sigla = sigla;
        this.idPais = idPais;
        this.estadoActivo = estadoActivo;
        // Copias propias: el agregado no comparte sus colecciones con quien lo construyo. En el
        // original las listas ni siquiera se inicializaban, de modo que agregar el primer correo
        // lanzaba NullPointerException.
        this.correos = new ArrayList<>(correos);
        this.telefonos = new ArrayList<>(telefonos);
        this.supervisores = new LinkedHashMap<>();
        supervisores.forEach(supervisor -> this.supervisores.put(supervisor.idPersona(), supervisor));
    }

    /**
     * Registra un cliente nuevo, con la sigla que le corresponde a su razón social.
     *
     * <p>Sin mirar si otro cliente ya la tiene: el desempate es del servicio, que sabe qué siglas
     * existen. Este camino queda para quien no necesita desempatar —las pruebas, sobre todo—.
     */
    public static Client create(
            String documento, IdentificationType tipoIdentificacion, String razonSocial, UUID idPais) {

        return create(documento, tipoIdentificacion, razonSocial, idPais,
                ClientAcronym.base(validarRazonSocial(razonSocial)));
    }

    /** Registra un cliente nuevo con la sigla ya desempatada. */
    public static Client create(
            String documento,
            IdentificationType tipoIdentificacion,
            String razonSocial,
            UUID idPais,
            String sigla) {

        Client cliente = new Client(
                UUID.randomUUID(),
                validarDocumento(documento),
                exigirTipo(tipoIdentificacion),
                validarRazonSocial(razonSocial),
                ClientAcronym.validar(sigla),
                idPais,
                true,
                List.of(),
                List.of(),
                List.of());

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
            String sigla,
            UUID idPais,
            boolean estadoActivo,
            List<EmailClient> correos,
            List<PhoneClient> telefonos,
            List<ClientSupervisor> supervisores) {

        return new Client(
                id, documento, tipoIdentificacion, razonSocial, sigla, idPais, estadoActivo,
                correos, telefonos, supervisores);
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

    /**
     * Corrige la sigla. No hace nada si es la que ya tenía.
     *
     * <p>Que no la tenga otro cliente lo comprueba el servicio. Las hojas de vida ya numeradas
     * conservan su número con la sigla vieja: decidido por el usuario el 2026-10-05, para que ningún
     * papel impreso deje de coincidir con el sistema.
     */
    public void changeAcronym(String sigla) {
        String nueva = ClientAcronym.validar(sigla);

        if (nueva.equals(this.sigla)) {
            // Un cambio que no cambia nada no emite evento.
            return;
        }

        this.sigla = nueva;
        registerEvent(new ClientUpdatedEvent(metadataFor(ClientUpdatedEvent.TYPE), payload()));
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
    // Supervisores
    // ---------------------------------------------------------------------------

    /**
     * Nombra supervisora a una persona, o le cambia el cargo si ya lo era.
     *
     * <p>Nombrar a quien ya lo es con el mismo cargo no hace nada. La relación es de muchos a muchos
     * —una persona puede supervisar a varios clientes—, y que la persona sea de las que supervisan lo
     * comprueba el servicio, que es quien puede preguntarle al módulo de personas.
     */
    public void appointSupervisor(UUID idPersona, String cargo) {
        ClientSupervisor nuevo = new ClientSupervisor(idPersona, cargo);
        if (nuevo.equals(supervisores.get(idPersona))) {
            return;
        }

        supervisores.put(idPersona, nuevo);
        registerEvent(new SupervisorAppointedEvent(
                metadataFor(SupervisorAppointedEvent.TYPE), new SupervisorPayload(idPersona, nuevo.cargo())));
    }

    /** Cambia el cargo de quien ya supervisa al cliente. Falla si no lo supervisa. */
    public void changeSupervisorTitle(UUID idPersona, String cargo) {
        if (!supervisores.containsKey(idPersona)) {
            throw new IllegalArgumentException(
                    "La persona " + idPersona + " no supervisa a este cliente");
        }

        appointSupervisor(idPersona, cargo);
    }

    /** Retira a una persona de la supervisión. Retirar a quien no lo es no hace nada. */
    public void removeSupervisor(UUID idPersona) {
        if (supervisores.remove(idPersona) == null) {
            return;
        }

        registerEvent(new SupervisorRemovedEvent(
                metadataFor(SupervisorRemovedEvent.TYPE), new SupervisorPayload(idPersona, null)));
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

    public List<ClientSupervisor> getSupervisores() {
        return List.copyOf(supervisores.values());
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
