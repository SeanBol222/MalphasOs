package com.malphasos.malphasos.equipment.application.services.model.commands;

import java.util.UUID;

/** Reemplaza la ficha técnica de un modelo entera: lo que no venga queda vacío. */
public record DescribeModelCommand(UUID id, TechnicalSheetCommand fichaTecnica) {
}
