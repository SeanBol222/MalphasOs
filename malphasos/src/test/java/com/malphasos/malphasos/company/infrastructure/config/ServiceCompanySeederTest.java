package com.malphasos.malphasos.company.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SeedServiceCompanyCommand;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La siembra del primer arranque (2026-10-17). */
@ExtendWith(MockitoExtension.class)
class ServiceCompanySeederTest {

    @Mock private ServiceCompanyServicePort servicio;

    @TempDir Path directorio;

    private ServiceCompanySeeder sembrador(String nombre, String logo) {
        ServiceCompanyProperties archivo = new ServiceCompanyProperties();
        archivo.setDirectorio(directorio.toString());
        archivo.setNombre(nombre);
        archivo.setGarantia("Garantía del archivo");
        archivo.setLogo(logo);

        return new ServiceCompanySeeder(archivo, servicio);
    }

    @Test
    @DisplayName("con la empresa ya en la base no mira el archivo, ni siquiera un logo que ya no existe")
    void conEmpresa() {
        when(servicio.find()).thenReturn(Optional.of(ServiceCompany.rehydrate(UUID.randomUUID(),
                new CompanyDetails("Ya", null, null, null, List.of(), null, null, null, null, null, null), null)));

        sembrador("Del archivo", "no-existe.png").run(null);

        verify(servicio, never()).seed(any());
    }

    @Test
    @DisplayName("sin empresa y sin archivo arranca igual: se llenará desde la pantalla")
    void sinArchivo() {
        when(servicio.find()).thenReturn(Optional.empty());

        sembrador(null, null).run(null);

        verify(servicio, never()).seed(any());
    }

    @Test
    @DisplayName("sin empresa y con archivo, la siembra con sus datos y su logo")
    void siembra() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        Files.write(directorio.resolve("logo.png"), png);
        when(servicio.find()).thenReturn(Optional.empty());

        sembrador("Del archivo", "logo.png").run(null);

        ArgumentCaptor<SeedServiceCompanyCommand> comando = ArgumentCaptor.forClass(SeedServiceCompanyCommand.class);
        verify(servicio).seed(comando.capture());
        assertThat(comando.getValue().datos().nombre()).isEqualTo("Del archivo");
        assertThat(comando.getValue().datos().garantia()).isEqualTo("Garantía del archivo");
        assertThat(comando.getValue().logo()).isEqualTo(png);
    }

    @Test
    @DisplayName("sin empresa y con un logo configurado que no existe, el arranque falla diciéndolo")
    void logoQueNoExiste() {
        when(servicio.find()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sembrador("Del archivo", "no-existe.png").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-existe.png");
    }
}
