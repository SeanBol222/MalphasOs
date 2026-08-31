package com.malphasos.malphasos.client.domain.manager;

/**
 * De qué se encarga un encargado.
 *
 * <p>En el original era un {@code String} cuyo javadoc documentaba {@code "sede"} y
 * {@code "area_servicio"}, dos valores que la restricción de la tabla <b>rechaza</b>: solo admite
 * {@code HEADQUARTER} y {@code SERVICE_AREA}. La documentación y el esquema decían cosas distintas.
 */
public enum ManagerType {
    HEADQUARTER,
    SERVICE_AREA
}
