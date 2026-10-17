package com.malphasos.malphasos.company.application.services.serviceCompany.commands;

/**
 * La empresa del archivo de arranque, para una base que todavía no tiene ninguna.
 *
 * @param logo los bytes del logo del archivo; nulo si no configura ninguno
 */
public record SeedServiceCompanyCommand(SaveServiceCompanyCommand datos, byte[] logo) {
}
