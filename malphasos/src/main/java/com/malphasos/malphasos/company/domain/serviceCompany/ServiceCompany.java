package com.malphasos.malphasos.company.domain.serviceCompany;

import com.malphasos.malphasos.company.domain.serviceCompany.events.ServiceCompanyConfiguredEvent;
import com.malphasos.malphasos.company.domain.serviceCompany.events.ServiceCompanyLogoChangedEvent;
import com.malphasos.malphasos.company.domain.serviceCompany.events.ServiceCompanyPayload;
import com.malphasos.malphasos.company.domain.serviceCompany.events.ServiceCompanyUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * La empresa que presta el servicio técnico: el membrete de la hoja de vida y del informe (2026-10-17).
 *
 * <p>Hay una sola. Hasta el 2026-10-16 sus datos se leían de un archivo al arrancar; el usuario decidió
 * que un administrador los edite desde la aplicación, logo incluido, y que el archivo solo siembre el
 * primer arranque. Un informe cerrado no cambia al editar la empresa: copia lo que imprime al cerrarse.
 *
 * <p>No se retira: existe o todavía no se ha configurado.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ServiceCompany extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private CompanyDetails datos;

    /** Nulo si no tiene: el membrete es entonces el nombre. */
    private CompanyLogo logo;

    private ServiceCompany(UUID id, CompanyDetails datos, CompanyLogo logo) {
        this.id = id;
        this.datos = Objects.requireNonNull(datos, "Los datos de la empresa no son nulos");
        this.logo = logo;
    }

    /** La configura por primera vez. */
    public static ServiceCompany configure(CompanyDetails datos) {
        ServiceCompany empresa = new ServiceCompany(UUID.randomUUID(), datos, null);
        empresa.registerEvent(new ServiceCompanyConfiguredEvent(
                empresa.metadataFor(ServiceCompanyConfiguredEvent.TYPE), empresa.payload()));

        return empresa;
    }

    public static ServiceCompany rehydrate(UUID id, CompanyDetails datos, CompanyLogo logo) {
        return new ServiceCompany(id, datos, logo);
    }

    /** Corrige sus datos: van siempre todos. No hace nada si son los que ya tenía. */
    public void update(CompanyDetails nuevos) {
        Objects.requireNonNull(nuevos, "Los datos de la empresa no son nulos");
        if (nuevos.equals(datos)) {
            return;
        }

        this.datos = nuevos;
        registerEvent(new ServiceCompanyUpdatedEvent(metadataFor(ServiceCompanyUpdatedEvent.TYPE), payload()));
    }

    /** Pone o cambia el logo. El mismo logo otra vez no emite evento. */
    public void changeLogo(CompanyLogo nuevo) {
        Objects.requireNonNull(nuevo, "Para quitar el logo está removeLogo");
        if (nuevo.equals(logo)) {
            return;
        }

        this.logo = nuevo;
        registerEvent(new ServiceCompanyLogoChangedEvent(
                metadataFor(ServiceCompanyLogoChangedEvent.TYPE), payload()));
    }

    /** Quita el logo. Sin logo no hace nada. */
    public void removeLogo() {
        if (logo == null) {
            return;
        }

        this.logo = null;
        registerEvent(new ServiceCompanyLogoChangedEvent(
                metadataFor(ServiceCompanyLogoChangedEvent.TYPE), payload()));
    }

    public Optional<CompanyLogo> logo() {
        return Optional.ofNullable(logo);
    }

    @Override
    protected String aggregateType() {
        return "ServiceCompany";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ServiceCompanyPayload payload() {
        return new ServiceCompanyPayload(datos.nombre(), datos.nit(), logo != null);
    }
}
