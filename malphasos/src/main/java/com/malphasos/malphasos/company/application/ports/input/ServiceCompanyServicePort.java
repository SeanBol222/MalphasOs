package com.malphasos.malphasos.company.application.ports.input;

import com.malphasos.malphasos.company.application.services.serviceCompany.commands.ChangeCompanyLogoCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SaveServiceCompanyCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SeedServiceCompanyCommand;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import java.util.Optional;

/**
 * La empresa que presta el servicio (2026-10-17). Es también lo que leen los demás módulos para poner
 * el membrete en sus documentos.
 */
public interface ServiceCompanyServicePort {

    /** La empresa, o vacío si todavía no se ha configurado. */
    Optional<ServiceCompany> find();

    /**
     * El nombre de la ciudad de la empresa, para el membrete (V44); nulo si no tiene. La empresa guarda
     * la ciudad por su identificador, y quien imprime necesita el nombre.
     */
    String cityName(ServiceCompany empresa);

    /**
     * Guarda sus datos: la configura si no existía y la corrige si sí.
     *
     * @throws com.malphasos.malphasos.location.domain.exception.CityNotFoundException si la ciudad no
     *     existe
     */
    ServiceCompany save(SaveServiceCompanyCommand command);

    /** Pone o cambia el logo. Exige la empresa ya configurada. */
    ServiceCompany changeLogo(ChangeCompanyLogoCommand command);

    /** Quita el logo. Exige la empresa ya configurada. */
    ServiceCompany removeLogo();

    /**
     * Crea la empresa del archivo de arranque si la base no tiene ninguna. Con una ya configurada no
     * hace nada: desde ese momento manda la base, y el archivo no pisa lo que un administrador editó.
     *
     * @return si la creó
     */
    boolean seed(SeedServiceCompanyCommand command);
}
