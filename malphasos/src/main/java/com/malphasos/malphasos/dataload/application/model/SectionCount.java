package com.malphasos.malphasos.dataload.application.model;

/** Cuántos registros de una sección crearía la carga, y cuántos ignoraría porque ya existen. */
public record SectionCount(String seccion, int nuevos, int existentes) {
}
