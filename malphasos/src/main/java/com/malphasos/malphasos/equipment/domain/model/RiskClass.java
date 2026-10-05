package com.malphasos.malphasos.equipment.domain.model;

/**
 * Clasificación de un dispositivo médico según su riesgo, como la fija el registro sanitario en
 * Colombia (Decreto 4725 de 2005): de menor a mayor, I, IIa, IIb y III.
 *
 * <p>Vive en el modelo y no en el tipo porque la fija el registro INVIMA, que es del modelo: dos
 * monitores de marcas distintas pueden estar registrados en clases distintas.
 */
public enum RiskClass {
    I,
    IIA,
    IIB,
    III
}
