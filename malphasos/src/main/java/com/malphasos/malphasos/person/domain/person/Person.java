package com.malphasos.malphasos.person.domain.person;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Persona física registrada en el sistema: ingenieros, encargados de sede y representantes
 * legales de los clientes.
 *
 * <p>Los nombres de los campos están en español porque son el vocabulario del dominio y de la
 * base de datos; cambiarlos obligaría a traducir mentalmente en cada consulta.
 *
 * <p>Correos y teléfonos se gestionan desde aquí, no como entidades independientes: no tienen
 * sentido fuera de la persona a la que pertenecen. Ninguno se borra físicamente, se marcan como
 * inactivos para conservar el historial.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Accessors(chain = true)
public class Person {

    /** Identificador interno de la persona dentro de la empresa. */
    private UUID identificador;

    /** Cédula, identificador único de la persona frente al Estado. */
    private String cedula;

    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;

    /** Rol principal: ingeniero, encargado o representante legal. */
    private String tipoPersona;

    /** Rol secundario, cuando una misma persona cumple dos funciones. */
    private String segundoTipoPersona;

    /** Borrado lógico: una persona inactiva conserva su historial. */
    private boolean estadoActivo;

    /**
     * Se inicializan vacías y no en {@code null}: {@link #addEmail} y {@link #addPhone} operan
     * sobre ellas directamente, y un objeto construido sin correos lanzaría
     * {@code NullPointerException} al agregar el primero.
     */
    @Builder.Default
    private List<EmailPerson> emailPersonList = new ArrayList<>();

    @Builder.Default
    private List<PhonePerson> phonePersonList = new ArrayList<>();

    public void addEmail(EmailPerson email) {
        this.emailPersonList.add(email);
    }

    /**
     * Desactiva un correo sin eliminarlo. Si el identificador no corresponde a ninguno de los
     * correos de esta persona, la llamada no tiene efecto.
     */
    public void removeEmail(UUID idEmail) {
        this.emailPersonList.stream()
                .filter(e -> e.getIdCorreoPersona().equals(idEmail))
                .findFirst()
                .ifPresent(e -> e.setEstadoActivo(false));
    }

    public void addPhone(PhonePerson phone) {
        this.phonePersonList.add(phone);
    }

    /**
     * Desactiva un teléfono sin eliminarlo. Si el identificador no corresponde a ninguno de los
     * teléfonos de esta persona, la llamada no tiene efecto.
     */
    public void removePhone(UUID idPhone) {
        this.phonePersonList.stream()
                .filter(p -> p.getIdTelefonoPersona().equals(idPhone))
                .findFirst()
                .ifPresent(p -> p.setEstadoActivo(false));
    }
}
