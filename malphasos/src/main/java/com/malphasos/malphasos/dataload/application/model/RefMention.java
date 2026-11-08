package com.malphasos.malphasos.dataload.application.model;

/**
 * Una {@code ref} escrita en un archivo, donde se declara o donde se usa: qué ref, qué clase de cosa
 * nombra, y dónde está, para decirlo en el informe.
 */
public record RefMention(String ref, RefKind clase, String archivo, String ruta) {
}
