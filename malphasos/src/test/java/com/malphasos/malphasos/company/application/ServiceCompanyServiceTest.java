package com.malphasos.malphasos.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.company.application.ports.output.ServiceCompanyPersistencePort;
import com.malphasos.malphasos.company.application.services.serviceCompany.ServiceCompanyService;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.ChangeCompanyLogoCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SaveServiceCompanyCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SeedServiceCompanyCommand;
import com.malphasos.malphasos.company.domain.exception.ServiceCompanyNotConfiguredException;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La empresa que presta el servicio (2026-10-17): se guarda, se le pone logo y se siembra. */
@ExtendWith(MockitoExtension.class)
class ServiceCompanyServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1};

    @Mock private ServiceCompanyPersistencePort persistencia;
    @Mock private EventDispatcherPort eventos;
    @Mock private CityServicePort ciudades;

    @InjectMocks private ServiceCompanyService servicio;

    private static SaveServiceCompanyCommand comando(String nombre, String garantia) {
        return new SaveServiceCompanyCommand(nombre, null, null, null, List.of(), null, null, garantia, null, null,
                null);
    }

    private static ServiceCompany existente() {
        return ServiceCompany.rehydrate(UUID.randomUUID(),
                new CompanyDetails("Ejemplo", null, null, null, List.of(), null, null, "Vieja", null, null, null),
                null);
    }

    @Test
    @DisplayName("guardar sin empresa la configura, y lo anuncia")
    void configura() {
        when(persistencia.find()).thenReturn(Optional.empty());
        when(persistencia.save(any())).thenAnswer(i -> i.getArgument(0));

        ServiceCompany guardada = servicio.save(comando("Ejemplo", "Nueva"));

        assertThat(guardada.getDatos().garantia()).isEqualTo("Nueva");
        verify(eventos).dispatchAll(any());
    }

    @Test
    @DisplayName("guardar con empresa la corrige a ella, no crea otra")
    void corrige() {
        ServiceCompany empresa = existente();
        when(persistencia.find()).thenReturn(Optional.of(empresa));
        when(persistencia.save(any())).thenAnswer(i -> i.getArgument(0));

        ServiceCompany guardada = servicio.save(comando("Ejemplo", "Nueva"));

        assertThat(guardada.getId()).isEqualTo(empresa.getId());
        assertThat(guardada.getDatos().garantia()).isEqualTo("Nueva");
    }

    @Test
    @DisplayName("guardar con una ciudad que no está en el catálogo falla antes de guardar nada (V44)")
    void ciudadQueNoExiste() {
        UUID ciudad = UUID.randomUUID();
        when(ciudades.findById(ciudad)).thenThrow(new CityNotFoundException(ciudad));

        assertThatThrownBy(() -> servicio.save(new SaveServiceCompanyCommand("Ejemplo", null, null, ciudad,
                        List.of(), null, null, null, null, null, null)))
                .isInstanceOf(CityNotFoundException.class);
        verify(persistencia, never()).save(any());
    }

    @Test
    @DisplayName("el nombre de su ciudad sale del catálogo, y sin ciudad no se pregunta (V44)")
    void nombreDeLaCiudad() {
        City bogota = City.rehydrate(UUID.randomUUID(), "Bogotá", UUID.randomUUID(), true);
        when(ciudades.findById(bogota.getId())).thenReturn(bogota);

        assertThat(servicio.cityName(ServiceCompany.rehydrate(UUID.randomUUID(),
                new CompanyDetails("Ejemplo", null, null, bogota.getId(), List.of(), null, null, null, null, null,
                        null),
                null))).isEqualTo("Bogotá");
        assertThat(servicio.cityName(existente())).isNull();
    }

    @Test
    @DisplayName("el logo exige la empresa configurada")
    void logoSinEmpresa() {
        when(persistencia.find()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.changeLogo(new ChangeCompanyLogoCommand(PNG)))
                .isInstanceOf(ServiceCompanyNotConfiguredException.class);
        assertThatThrownBy(() -> servicio.removeLogo()).isInstanceOf(ServiceCompanyNotConfiguredException.class);
        verify(persistencia, never()).save(any());
    }

    @Test
    @DisplayName("con empresa, el logo se pone y se quita")
    void logo() {
        ServiceCompany empresa = existente();
        when(persistencia.find()).thenReturn(Optional.of(empresa));
        when(persistencia.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(servicio.changeLogo(new ChangeCompanyLogoCommand(PNG)).logo()).isPresent();
        assertThat(servicio.removeLogo().logo()).isEmpty();
    }

    @Test
    @DisplayName("sembrar con la empresa ya en la base no hace nada: no pisa lo que un administrador editó")
    void siembraNoPisa() {
        when(persistencia.find()).thenReturn(Optional.of(existente()));

        assertThat(servicio.seed(new SeedServiceCompanyCommand(comando("Del archivo", null), PNG))).isFalse();
        verify(persistencia, never()).save(any());
    }

    @Test
    @DisplayName("sembrar en una base vacía crea la empresa del archivo, con su logo")
    void siembra() {
        when(persistencia.find()).thenReturn(Optional.empty());
        when(persistencia.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(servicio.seed(new SeedServiceCompanyCommand(comando("Del archivo", null), PNG))).isTrue();

        ArgumentCaptor<ServiceCompany> guardada = ArgumentCaptor.forClass(ServiceCompany.class);
        verify(persistencia).save(guardada.capture());
        assertThat(guardada.getValue().getDatos().nombre()).isEqualTo("Del archivo");
        assertThat(guardada.getValue().logo()).isPresent();
    }
}
