package com.malphasos.malphasos.company.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * El almacén de la empresa, contra PostgreSQL real (V36). Se vacía antes de cada prueba: el contexto de
 * pruebas siembra la empresa inventada al arrancar, como una instalación nueva.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM telefono_empresa", "DELETE FROM empresa"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = {"DELETE FROM telefono_empresa", "DELETE FROM empresa"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ServiceCompanyPersistenceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

    @Autowired private ServiceCompanyPersistenceAdapter almacen;
    @Autowired private JdbcTemplate jdbc;

    private static CompanyDetails datos(String nombre) {
        return new CompanyDetails(nombre, "900.000.000-0", "Carrera 1", "Ciudad", List.of("601 1", "601 2"),
                "300", "a@b.example", "Garantía", "Aclaración", "FT-1", "FT-2");
    }

    @Test
    @DisplayName("sin fila no hay empresa")
    void vacia() {
        assertThat(almacen.find()).isEmpty();
    }

    @Test
    @DisplayName("ida y vuelta con todos sus campos: los teléfonos en su orden y el logo con su tipo")
    void idaYVuelta() {
        ServiceCompany empresa = ServiceCompany.configure(datos("Ejemplo"));
        empresa.changeLogo(CompanyLogo.of(PNG));
        almacen.save(empresa);

        ServiceCompany leida = almacen.find().orElseThrow();

        assertThat(leida.getId()).isEqualTo(empresa.getId());
        assertThat(leida.getDatos()).isEqualTo(datos("Ejemplo"));
        assertThat(leida.logo()).contains(CompanyLogo.of(PNG));
    }

    @Test
    @DisplayName("corregirla y quitarle el logo actualiza la misma fila")
    void corregir() {
        ServiceCompany empresa = ServiceCompany.configure(datos("Ejemplo"));
        empresa.changeLogo(CompanyLogo.of(PNG));
        almacen.save(empresa);

        ServiceCompany leida = almacen.find().orElseThrow();
        leida.update(datos("Otro nombre"));
        leida.removeLogo();
        almacen.save(leida);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM empresa", Integer.class)).isEqualTo(1);
        assertThat(almacen.find().orElseThrow().getDatos().nombre()).isEqualTo("Otro nombre");
        assertThat(almacen.find().orElseThrow().logo()).isEmpty();
    }

    @Test
    @DisplayName("los teléfonos se reescriben enteros: menos, y en otro orden (V43)")
    void telefonosReescritos() {
        ServiceCompany empresa = ServiceCompany.configure(datos("Ejemplo"));
        almacen.save(empresa);

        ServiceCompany leida = almacen.find().orElseThrow();
        CompanyDetails antes = leida.getDatos();
        leida.update(new CompanyDetails(antes.nombre(), antes.nit(), antes.direccion(), antes.ciudad(),
                List.of("601 2"), antes.movil(), antes.correo(), antes.garantia(), antes.aclaracion(),
                antes.codigoHojaDeVida(), antes.codigoInforme()));
        almacen.save(leida);

        assertThat(almacen.find().orElseThrow().getDatos().telefonos()).containsExactly("601 2");
        assertThat(jdbc.queryForList("SELECT i_orden FROM telefono_empresa", Integer.class)).containsExactly(0);
    }

    @Test
    @DisplayName("la base impide un teléfono de la empresa en blanco (V43)")
    void telefonoEnBlanco() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (k_id_empresa, n_nombre) VALUES (?, 'E')", id);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO telefono_empresa (k_id_empresa, i_orden, n_telefono_empresa) VALUES (?, 0, ' ')",
                        id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la base impide una segunda empresa, aunque se salte el servicio")
    void unaSola() {
        almacen.save(ServiceCompany.configure(datos("Una")));

        assertThatThrownBy(() -> almacen.save(ServiceCompany.configure(datos("Otra"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la base impide un logo sin tipo, o con un tipo que no es imagen")
    void logoConSuTipo() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (k_id_empresa, n_nombre) VALUES (?, 'E')", id);

        assertThatThrownBy(() -> jdbc.update("UPDATE empresa SET b_logo = ? WHERE k_id_empresa = ?", PNG, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE empresa SET b_logo = ?, t_logo_tipo = 'text/html' WHERE k_id_empresa = ?", PNG, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(
                "UPDATE empresa SET b_logo = ?, t_logo_tipo = 'image/png' WHERE k_id_empresa = ?", PNG, id))
                .isEqualTo(1);
    }
}
