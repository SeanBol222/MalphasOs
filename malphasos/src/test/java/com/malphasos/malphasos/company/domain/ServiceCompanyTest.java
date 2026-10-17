package com.malphasos.malphasos.company.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.company.domain.serviceCompany.events.ServiceCompanyPayload;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La empresa que presta el servicio (2026-10-17). */
class ServiceCompanyTest {

    private static final CompanyDetails DATOS =
            new CompanyDetails("Ejemplo S.A.S.", "900", null, null, List.of(), null, null, "Garantía", null, null, null);

    private static List<String> tipos(ServiceCompany empresa) {
        return empresa.pullEvents().stream().map(evento -> evento.metadata().eventType()).toList();
    }

    @Test
    @DisplayName("configurarla lo anuncia, sin logo")
    void configurar() {
        ServiceCompany empresa = ServiceCompany.configure(DATOS);

        assertThat(empresa.logo()).isEmpty();
        List<DomainEvent<?>> eventos = List.copyOf(empresa.pullEvents());
        assertThat(eventos).extracting(evento -> evento.metadata().eventType())
                .containsExactly("service-company.configured");
        assertThat(eventos.getFirst().payload()).isEqualTo(new ServiceCompanyPayload("Ejemplo S.A.S.", "900", false));
    }

    @Test
    @DisplayName("corregirla con los mismos datos no anuncia nada; con otros, sí")
    void corregir() {
        ServiceCompany empresa = ServiceCompany.rehydrate(UUID.randomUUID(), DATOS, null);

        empresa.update(new CompanyDetails(" Ejemplo S.A.S. ", "900", "", null, null, null, null, "Garantía", null,
                null, null));
        assertThat(tipos(empresa)).isEmpty();

        empresa.update(new CompanyDetails("Ejemplo S.A.S.", "900", null, null, List.of(), null, null,
                "Otra garantía", null, null, null));
        assertThat(tipos(empresa)).containsExactly("service-company.updated");
        assertThat(empresa.getDatos().garantia()).isEqualTo("Otra garantía");
    }

    @Test
    @DisplayName("poner el logo lo anuncia, el mismo otra vez no, y quitarlo sí")
    void logo() {
        ServiceCompany empresa = ServiceCompany.rehydrate(UUID.randomUUID(), DATOS, null);

        empresa.changeLogo(CompanyLogo.of(CompanyLogoTest.PNG));
        empresa.changeLogo(CompanyLogo.of(CompanyLogoTest.PNG));
        assertThat(tipos(empresa)).containsExactly("service-company.logo-changed");
        assertThat(empresa.logo()).contains(CompanyLogo.of(CompanyLogoTest.PNG));

        empresa.removeLogo();
        assertThat(tipos(empresa)).containsExactly("service-company.logo-changed");
        assertThat(empresa.logo()).isEmpty();
    }

    @Test
    @DisplayName("quitar el logo a una empresa sin logo no anuncia nada")
    void quitarSinLogo() {
        ServiceCompany empresa = ServiceCompany.rehydrate(UUID.randomUUID(), DATOS, null);

        empresa.removeLogo();

        assertThat(tipos(empresa)).isEmpty();
    }

    @Test
    @DisplayName("rehidratarla no anuncia nada: leer de la base no es un hecho del dominio")
    void rehidratar() {
        ServiceCompany empresa = ServiceCompany.rehydrate(UUID.randomUUID(), DATOS, CompanyLogo.of(CompanyLogoTest.PNG));

        assertThat(empresa.pullEvents()).isEmpty();
    }
}
