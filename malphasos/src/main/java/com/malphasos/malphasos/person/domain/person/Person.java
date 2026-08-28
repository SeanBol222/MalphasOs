package com.malphasos.malphasos.person.domain.person;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

    /** Función principal de la persona en el negocio. */
    private PersonType tipoPersona;

    /** Función secundaria, cuando una misma persona cumple dos papeles. */
    private PersonType segundoTipoPersona;

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

    /** Tipos que no admiten una función secundaria: quien ocupa uno de estos no es además encargado. */
    private static final Set<PersonType> TYPES_WITHOUT_SECOND_TYPE =
            EnumSet.of(PersonType.ENGINEER, PersonType.ADMIN, PersonType.SUPER_ADMIN);

    /**
     * Comprueba que la combinación de rol principal y secundario sea válida.
     *
     * <p>Estas reglas existían en el esquema original como la función {@code validar_roles_persona()},
     * pero nunca llegaron a ejecutarse: faltaba el {@code CREATE TRIGGER} que la invocara. Viven aquí
     * porque son lógica de negocio, y en el dominio pueden producir un mensaje de error útil y
     * probarse sin base de datos. La migración conserva únicamente la restricción de valores
     * permitidos, como última línea de defensa ante escrituras directas por SQL.
     *
     * @throws IllegalArgumentException si la combinación de roles no es válida
     */
    public void validateRoles() {

        if (segundoTipoPersona == null) {
            return;
        }

        if (TYPES_WITHOUT_SECOND_TYPE.contains(tipoPersona)) {
            throw new IllegalArgumentException(
                    "Una persona de tipo " + tipoPersona + " no puede tener un segundo tipo");
        }

        if (segundoTipoPersona == tipoPersona) {
            throw new IllegalArgumentException(
                    "El tipo principal y el secundario no pueden ser el mismo: " + tipoPersona);
        }
    }

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
