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
 * persona para el negocio, mientras que el rol determina qué puede hacer en el sistema.
 *
 * <p>Tenía cinco valores hasta V53 (2026-11-01): el encargado de una sede o un área ({@code MANAGER})
 * y el representante legal del cliente ({@code CEO_CLIENT}) se juntaron en {@link #SUPERVISOR}, la
 * única persona del cliente, que siempre tiene cuenta.
 */
public enum PersonType {

    /** Personal técnico que ejecuta los mantenimientos. */
    ENGINEER,

    /**
     * Persona del cliente: ve lo de los clientes que supervisa y aprueba sus órdenes. A cuáles lo dice
     * {@code supervisor_cliente}, en el módulo de clientes.
     */
    SUPERVISOR,

    /** Administrador del sistema. */
    ADMIN,

    /** Administrador con privilegios totales. */
    SUPER_ADMIN
}
