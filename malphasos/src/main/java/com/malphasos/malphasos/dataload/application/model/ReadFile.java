package com.malphasos.malphasos.dataload.application.model;

/** Un archivo de la carga que cumplió el esquema, con su nombre para decir dónde está cada problema. */
public record ReadFile(String nombre, LoadDocument documento) {
}
