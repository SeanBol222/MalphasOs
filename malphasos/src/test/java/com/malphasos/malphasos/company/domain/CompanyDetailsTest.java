package com.malphasos.malphasos.company.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los datos de la empresa (2026-10-17): solo el nombre es obligatorio, y lo que queda en blanco no hay. */
class CompanyDetailsTest {

    private static CompanyDetails conNombre(String nombre) {
        return new CompanyDetails(nombre, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("sin nombre no hay empresa: es lo que encabeza sus documentos")
    void nombreObligatorio() {
        assertThatIllegalArgumentException().isThrownBy(() -> conNombre(null));
        assertThatIllegalArgumentException().isThrownBy(() -> conNombre("   ")).withMessageContaining("nombre");
    }

    @Test
    @DisplayName("se recorta todo, y lo que queda en blanco es nulo")
    void recortes() {
        CompanyDetails datos = new CompanyDetails(" Ejemplo ", " 900 ", "  ", null, null, " 300 ", "",
                " Garantía ", "\t", " FT-1 ", " ");

        assertThat(datos.nombre()).isEqualTo("Ejemplo");
        assertThat(datos.nit()).isEqualTo("900");
        assertThat(datos.direccion()).isNull();
        assertThat(datos.movil()).isEqualTo("300");
        assertThat(datos.correo()).isNull();
        assertThat(datos.garantia()).isEqualTo("Garantía");
        assertThat(datos.aclaracion()).isNull();
        assertThat(datos.codigoHojaDeVida()).isEqualTo("FT-1");
        assertThat(datos.codigoInforme()).isNull();
    }

    @Test
    @DisplayName("los teléfonos llegan recortados, sin los vacíos y en su orden; sin lista es la lista vacía")
    void telefonos() {
        CompanyDetails datos = new CompanyDetails("E", null, null, null,
                Arrays.asList(" 601 1 ", null, " ", "602 2"), null, null, null, null, null, null);

        assertThat(datos.telefonos()).containsExactly("601 1", "602 2");
        assertThat(conNombre("E").telefonos()).isEmpty();
    }

    @Test
    @DisplayName("hasta cinco teléfonos fijos")
    void cincoTelefonos() {
        List<String> cinco = List.of("1", "2", "3", "4", "5");
        List<String> seis = List.of("1", "2", "3", "4", "5", "6");

        assertThat(new CompanyDetails("E", null, null, null, cinco, null, null, null, null, null, null).telefonos())
                .hasSize(5);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CompanyDetails("E", null, null, null, seis, null, null, null, null, null, null))
                .withMessageContaining("5 teléfonos");
    }

    @Test
    @DisplayName("cada campo tiene su largo, y el mensaje dice cuál")
    void largos() {
        assertThatIllegalArgumentException().isThrownBy(() -> conNombre("x".repeat(161)))
                .withMessageContaining("nombre");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CompanyDetails("E", null, null, null, null, null, null,
                        "x".repeat(2001), null, null, null))
                .withMessageContaining("garantía");
        assertThat(new CompanyDetails("E", null, null, null, null, null, null, "x".repeat(2000), null, null, null)
                .garantia()).hasSize(2000);
    }
}
