package com.malphasos.malphasos.person.domain.person;

/**
 * Función que cumple una persona dentro del negocio.
 *
 * <p>Es el vocabulario canónico del dominio y se corresponde exactamente con los valores que
 * admite la restricción {@code CHK_tipo_persona} de la tabla {@code persona}. Existe como enum, y
 * no como texto libre, para que un valor inválido sea imposible de escribir en tiempo de
 * compilación en lugar de descubrirse al insertar en la base de datos.
 *
 * <p>No confundir con {@link RoleType}, que representa el rol con el que se da de alta al usuario
 * en Keycloak. Son conceptos distintos y deliberadamente desacoplados: el tipo describe qué es la
 * persona para el negocio, mientras que el rol determina qué puede hacer en el sistema. No todas
 * las personas registradas necesitan usuario: un {@link #MANAGER} puede existir como contacto de
 * una sede sin acceder nunca a la aplicación.
 */
public enum PersonType {

    /** Personal técnico que ejecuta los mantenimientos. */
    ENGINEER,

    /** Encargado de una sede o de un área de servicio del cliente. */
    MANAGER,

    /** Representante legal del cliente. */
    CEO_CLIENT,

    /** Administrador del sistema. */
    ADMIN,

    /** Administrador con privilegios totales. */
    SUPER_ADMIN
}
