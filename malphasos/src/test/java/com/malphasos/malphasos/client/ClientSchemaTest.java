package com.malphasos.malphasos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.CodigoIsoLibre;
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
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM supervisor_cliente",
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

    /**
     * Diez digitos exactos, con ceros a la izquierda si hacen falta.
     *
     * <p>El formato fijo no es cosmetico: {@code k_cedula} es {@code varchar(10)} y
     * {@code k_documento} {@code varchar(11)}, asi que el valor se recortaba a diez caracteres. Un
     * resto de {@code nanoTime()} por debajo de mil millones tiene nueve digitos, y recortarlo
     * lanzaba {@code StringIndexOutOfBoundsException} en una de cada cien ejecuciones. Rellenando a
     * la izquierda la longitud deja de depender del reloj.
     */
    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private UUID insertClient(String tipoIdentificacion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, ?, 'Hospital Central')
                """,
                id, unico(), tipoIdentificacion);

        return id;
    }

    private UUID insertCity() {
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        long n = System.nanoTime();
        String iso = CodigoIsoLibre.en(jdbcTemplate);

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
                VALUES (?, ?, 'Ada', 'Lovelace', 'SUPERVISOR')
                """,
                id, unico());

        return id;
    }

    private void insertSupervisor(UUID persona, UUID cliente, String cargo) {
        jdbcTemplate.update(
                "INSERT INTO supervisor_cliente (k_identificador, k_id_cliente, n_cargo) VALUES (?, ?, ?)",
                persona, cliente, cargo);
    }

    @Test
    @DisplayName("las seis tablas del modulo existen tras la migracion, y ni encargados ni representantes")
    void migracionCreaLasTablas() {
        // Eran siete hasta V41, el 2026-10-27, cuando encargado se partio en una tabla por tipo, y ocho
        // hasta V53, el 2026-11-01: salieron los encargados y el representante legal paso a supervisor.
        Integer tablas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('cliente', 'correo_cliente', 'telefono_cliente',
                                     'supervisor_cliente', 'sede', 'area_servicio')
                """,
                Integer.class);
        Integer viejas = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('encargado', 'encargado_sede', 'encargado_area', 'representante_legal')
                """,
                Integer.class);

        assertThat(tablas).isEqualTo(6);
        assertThat(viejas).isZero();
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
    @DisplayName("una persona si puede supervisar a varios clientes")
    void supervisorDeVariosClientes() {
        UUID persona = insertPerson();
        UUID uno = insertClient("NIT_juridico");
        UUID otro = insertClient("NIT_natural");

        insertSupervisor(persona, uno, null);

        // La relacion es de muchos a muchos: la llave es compuesta.
        assertThatCode(() -> insertSupervisor(persona, otro, "Ingeniero biomédico"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la misma persona no se registra dos veces para el mismo cliente")
    void supervisorNoDuplicado() {
        UUID persona = insertPerson();
        UUID cliente = insertClient("NIT_juridico");

        insertSupervisor(persona, cliente, null);

        assertThatThrownBy(() -> insertSupervisor(persona, cliente, "Médico"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un cargo en blanco no entra: sin cargo es nulo")
    void cargoEnBlanco() {
        UUID cliente = insertClient("NIT_juridico");

        assertThatThrownBy(() -> insertSupervisor(insertPerson(), cliente, "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertSupervisor(insertPerson(), cliente, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la gente del cliente es SUPERVISOR: MANAGER y CEO_CLIENT ya no son tipos (V53)")
    void tiposDePersona() {
        for (String viejo : java.util.List.of("MANAGER", "CEO_CLIENT")) {
            assertThatThrownBy(() -> jdbcTemplate.update(
                            """
                            INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido,
                                                 t_tipo_persona)
                            VALUES (?, ?, 'Ada', 'Lovelace', ?)
                            """,
                            UUID.randomUUID(), unico(), viejo))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
