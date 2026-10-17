package com.malphasos.malphasos.company.application.services.serviceCompany.commands;

/** El logo nuevo, tal como se subió: el tipo lo deducen sus bytes, no quien lo sube. */
public record ChangeCompanyLogoCommand(byte[] contenido) {
}
