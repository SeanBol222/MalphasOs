package com.malphasos.malphasos.person.domain.person;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Rol con el que se da de alta a una persona en el proveedor de identidad.
 *
 * <p>Determina a qué grupo de Keycloak se asigna el usuario y, por tanto, qué puede hacer en el
 * sistema.
 *
 * <p><b>No es lo mismo que {@link PersonType}</b>, aunque tres de sus valores coincidan por nombre.
 * {@code PersonType} describe qué es la persona para el negocio y tiene cuatro valores; este enum
 * describe con qué permisos accede al sistema y tiene tres. En el proyecto original ambos conceptos
 * viajan por separado —{@code createUser} recibe el rol como parámetro, independiente del tipo de la
 * persona— y esa separación se conserva a propósito: quien accede no siempre lo hace con el rol que
 * sugiere su tipo.
 *
 * <p>El tipo {@code SUPER_ADMIN} de {@link PersonType} no tiene equivalente aquí: el alta de super
 * administradores quedó sin implementar en el original, y el escalón de arriba se otorga a mano.
 */
@Getter
@RequiredArgsConstructor
public enum RoleType {

    /** Personal técnico que ejecuta los mantenimientos. */
    ENGINEER("ENGINEER"),

    /** Administrador del sistema. */
    ADMIN("ADMIN"),

    /** Supervisor de un cliente, con acceso a la información de los clientes que supervisa. */
    SUPERVISOR("SUPERVISOR");

    private final String name;
}
