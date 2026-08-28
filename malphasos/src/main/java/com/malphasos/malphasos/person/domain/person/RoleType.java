package com.malphasos.malphasos.person.domain.person;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Rol con el que se da de alta a una persona en el proveedor de identidad.
 *
 * <p>Determina a qué grupo de Keycloak se asigna el usuario y, por tanto, qué puede hacer en el
 * sistema.
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
