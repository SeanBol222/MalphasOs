package com.malphasos.malphasos.equipment.application.services.model.commands;

import java.util.UUID;

/**
 * Corrige el nombre de un modelo.
 *
 * <p>Operación propia y no un campo del alta corregible, igual que renombrar una marca: el nombre es lo
 * que identifica al modelo en una pantalla, y cambiarlo es una decisión, no un retoque de datos.
 */
public record RenameModelCommand(UUID id, String nombre) {
}
