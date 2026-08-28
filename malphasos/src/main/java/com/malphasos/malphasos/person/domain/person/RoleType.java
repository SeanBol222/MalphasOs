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
 * {@code PersonType} describe qué es la persona para el negocio y tiene cinco valores; este enum
 * describe con qué permisos accede al sistema y tiene tres. En el proyecto original ambos conceptos
 * viajan por separado —{@code createUser} recibe el rol como parámetro, independiente del tipo de la
 * persona— y esa separación se conserva a propósito: no toda persona registrada necesita usuario, y
 * quien lo necesita no siempre accede con el rol que sugiere su tipo.
 *
 * <p>Los tipos {@code MANAGER} y {@code SUPER_ADMIN} de {@link PersonType} no tienen equivalente
 * aquí: un encargado puede existir como contacto de una sede sin acceder nunca a la aplicación, y el
 * alta de super administradores quedó sin implementar en el original.
 */
@Getter
@RequiredArgsConstructor
public enum RoleType {

    /** Personal técnico que ejecuta los mantenimientos. */
    ENGINEER("ENGINEER"),

    /** Administrador del sistema. */
    ADMIN("ADMIN"),

    /** Representante legal del cliente, con acceso a la información de su propia organización. */
    CEO_CLIENT("CEO_CLIENT");

    private final String name;
}
