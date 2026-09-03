package com.malphasos.malphasos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
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
 * Verifica que la migración V4 crea el esquema de clientes y que sus restricciones rechazan los
 * datos inválidos. Casi ninguna de estas reglas existía en el esquema original, así que estas
 * pruebas fijan lo que se decidió añadir.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM encargado",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM representante_legal",
            "DELETE FROM correo_cliente",
            "DELETE FROM telefono_cliente",
            "DELETE FROM cliente",
            "DELETE FROM ciudad",
            "DELETE FROM pais",
            "DELETE FROM persona"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientSchemaTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.valueOf(System.nanoTime() % 100_000_000_000L);
    }

    private UUID insertClient(String tipoIdentificacion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, ?, 'Hospital Central')
                """,
                id, unico().substring(0, 10), tipoIdentificacion);

        return id;
    }

    private UUID insertCity() {
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        long n = System.nanoTime();
        String letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String iso = "" + letras.charAt((int) (n % 26))
                + letras.charAt((int) ((n / 26) % 26))
                + letras.charAt((int) ((n / 676) % 26));

        jdbcTemplate.update(
                "INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                pais, iso, "Pais " + n);
        jdbcTemplate.update(
                "INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Ciudad " + n, pais);

        return ciudad;
    }

    private UUID insertHeadquarter(UUID cliente, UUID ciudad, String nombre) {
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero,
                                  k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30-40', ?, ?)
                """,
                sede, nombre, cliente, ciudad);

        return sede;
    }

    private UUID insertPerson() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido,
                                     t_tipo_persona)
                VALUES (?, ?, 'Ada', 'Lovelace', 'MANAGER')
                """,
                id, unico().substring(0, 10));

        return id;
    }

    private void insertManager(UUID persona, String tipo, UUID sede, UUID area) {
        jdbcTemplate.update(
                """
                INSERT INTO encargado (k_identificador, t_tipo_encargado, k_id_sede, k_id_area_servicio)
                VALUES (?, ?, ?, ?)
                """,
                persona, tipo, sede, area);
    }

    @Test
    @DisplayName("las siete tablas del modulo existen tras la migracion")
    void migracionCreaLasTablas() {
        Integer tablas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('cliente', 'correo_cliente', 'telefono_cliente',
                                     'representante_legal', 'sede', 'area_servicio', 'encargado')
                """,
                Integer.class);

        assertThat(tablas).isEqualTo(7);
    }

    @Test
    @DisplayName("el documento del cliente no se puede repetir")
    void documentoUnico() {
        UUID id = insertClient("NIT_juridico");
        String documento = jdbcTemplate.queryForObject(
                "SELECT k_documento FROM cliente WHERE k_id_cliente = ?", String.class, id);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                        VALUES (?, ?, 'CC', 'Otro')
                        """,
                        UUID.randomUUID(), documento))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el tipo de identificacion solo admite los cuatro valores del catalogo")
    void tipoDeIdentificacion() {
        assertThatCode(() -> insertClient("NIT_natural")).doesNotThrowAnyException();

        assertThatThrownBy(() -> insertClient("PASAPORTE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un correo de cliente no puede quedarse sin dueno")
    void correoSinCliente() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO correo_cliente (k_id_correo_cliente, n_correo_cliente, k_id_cliente)
                        VALUES (?, 'huerfano@cliente.com', NULL)
                        """,
                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un cliente no tiene dos sedes con el mismo nombre")
    void sedeUnicaPorCliente() {
        UUID cliente = insertClient("NIT_juridico");
        UUID ciudad = insertCity();
        insertHeadquarter(cliente, ciudad, "Sede Norte");

        assertThatThrownBy(() -> insertHeadquarter(cliente, ciudad, "Sede Norte"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("dos clientes distintos si pueden llamar igual a sus sedes")
    void mismoNombreDeSedeEnClientesDistintos() {
        UUID ciudad = insertCity();
        insertHeadquarter(insertClient("NIT_juridico"), ciudad, "Sede Norte");

        assertThatCode(() -> insertHeadquarter(insertClient("CC"), ciudad, "Sede Norte"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un encargado de sede necesita sede y no puede tener area")
    void encargadoDeSede() {
        UUID sede = insertHeadquarter(insertClient("NIT_juridico"), insertCity(), "Sede Norte");

        assertThatCode(() -> insertManager(insertPerson(), "HEADQUARTER", sede, null))
                .doesNotThrowAnyException();

        // El original dejaba ambas columnas anulables sin atarlas al tipo.
        assertThatThrownBy(() -> insertManager(insertPerson(), "HEADQUARTER", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un encargado de area necesita area y no puede tener sede")
    void encargadoDeArea() {
        UUID sede = insertHeadquarter(insertClient("NIT_juridico"), insertCity(), "Sede Norte");
        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, 'UCI', ?)",
                area, sede);

        assertThatCode(() -> insertManager(insertPerson(), "SERVICE_AREA", null, area))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertManager(insertPerson(), "SERVICE_AREA", sede, area))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un encargado es una persona: sin persona no hay encargado")
    void encargadoExigePersona() {
        UUID sede = insertHeadquarter(insertClient("NIT_juridico"), insertCity(), "Sede Norte");

        assertThatThrownBy(() -> insertManager(UUID.randomUUID(), "HEADQUARTER", sede, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("no puede haber dos encargados sobre la misma persona")
    void unEncargadoPorPersona() {
        UUID sede = insertHeadquarter(insertClient("NIT_juridico"), insertCity(), "Sede Norte");
        UUID persona = insertPerson();
        insertManager(persona, "HEADQUARTER", sede, null);

        assertThatThrownBy(() -> insertManager(persona, "HEADQUARTER", sede, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una persona si puede representar legalmente a varios clientes")
    void representanteDeVariosClientes() {
        UUID persona = insertPerson();
        UUID uno = insertClient("NIT_juridico");
        UUID otro = insertClient("NIT_natural");

        jdbcTemplate.update(
                "INSERT INTO representante_legal (k_identificador, k_id_cliente) VALUES (?, ?)",
                persona, uno);

        // A diferencia de encargado, la relacion es de muchos a muchos: la llave es compuesta.
        assertThatCode(() -> jdbcTemplate.update(
                        "INSERT INTO representante_legal (k_identificador, k_id_cliente) VALUES (?, ?)",
                        persona, otro))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la misma persona no se registra dos veces para el mismo cliente")
    void representanteNoDuplicado() {
        UUID persona = insertPerson();
        UUID cliente = insertClient("NIT_juridico");

        jdbcTemplate.update(
                "INSERT INTO representante_legal (k_identificador, k_id_cliente) VALUES (?, ?)",
                persona, cliente);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO representante_legal (k_identificador, k_id_cliente) VALUES (?, ?)",
                        persona, cliente))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
