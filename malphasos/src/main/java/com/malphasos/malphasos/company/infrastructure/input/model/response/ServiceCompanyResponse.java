package com.malphasos.malphasos.company.infrastructure.input.model.response;

import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** La empresa, como la ve quien la edita. Lo opcional que falta viaja nulo. */
@Schema(name = "ServiceCompanyResponse")
public record ServiceCompanyResponse(
        UUID id,
        String nombre,
        String nit,
        String direccion,
        @Schema(description = "La ciudad, del catalogo; nula si no tiene") UUID idCiudad,
        @Schema(description = "Telefonos fijos; vacia si no tiene") List<String> telefonos,
        String movil,
        String correo,
        String garantia,
        String aclaracion,
        String codigoHojaDeVida,
        String codigoInforme,
        @Schema(description = "El logo como data: URI; nulo si no hay") String logo) {

    public static ServiceCompanyResponse from(ServiceCompany empresa) {
        CompanyDetails datos = empresa.getDatos();

        return new ServiceCompanyResponse(empresa.getId(), datos.nombre(), datos.nit(), datos.direccion(),
                datos.idCiudad(), datos.telefonos(), datos.movil(), datos.correo(), datos.garantia(),
                datos.aclaracion(), datos.codigoHojaDeVida(), datos.codigoInforme(),
                empresa.logo().map(CompanyLogo::dataUri).orElse(null));
    }
}
