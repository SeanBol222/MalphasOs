package com.malphasos.malphasos.company.application.services.serviceCompany.commands;

import java.util.List;
import java.util.UUID;

/** Los datos de la empresa, todos: lo que no venga queda vacío. */
public record SaveServiceCompanyCommand(
        String nombre,
        String nit,
        String direccion,
        UUID idCiudad,
        List<String> telefonos,
        String movil,
        String correo,
        String garantia,
        String aclaracion,
        String codigoHojaDeVida,
        String codigoInforme) {
}
