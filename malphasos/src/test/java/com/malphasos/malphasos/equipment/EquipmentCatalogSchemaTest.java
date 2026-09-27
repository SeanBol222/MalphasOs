package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.math.BigDecimal;
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
 * Verifica que la migración V5 crea el catálogo de equipos y que sus restricciones rechazan los
 * datos inválidos. Casi ninguna existía en el esquema original.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM marca",
            "DELETE FROM punto_verificacion",
            "DELETE FROM tipo_equipo",
            "DELETE FROM fabricante"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EquipmentCatalogSchemaTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.valueOf(System.nanoTime());
    }

    private UUID insertBrand() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", id, "Marca " + unico());

        return id;
    }

    private UUID insertType(boolean verificable, String modalidad, BigDecimal amperaje) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         d_amperaje, b_verificable, n_tipo_verificacion,
                                         i_cantidad_datos, m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', ?, ?, ?, ?, 150000)
                """,
                id, "Tipo " + unico(), amperaje, verificable, modalidad, cantidadPara(modalidad));

        return id;
    }

    /**
     * La cantidad de datos que la modalidad exige, o {@code null} si no admite ninguna.
     *
     * <p>Desde {@code V8} las dos modalidades constantes la exigen y la variable la prohibe, de modo que
     * este ayudante ya no puede insertar un tipo sin decidirlo.
     */
    private Integer cantidadPara(String modalidad) {
        return "patron_constante".equals(modalidad) || "equipo_constante".equals(modalidad) ? 3 : null;
    }

    private UUID insertPoint(UUID tipo, String valor, String unidad, boolean activo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO punto_verificacion (k_id_punto_verificacion, k_id_tipo_equipo, d_valor,
                                                n_unidad, b_estado_activo)
                VALUES (?, ?, CAST(? AS numeric), ?, ?)
                """,
                id, tipo, valor, unidad, activo);

        return id;
    }

    private UUID insertEquipment(UUID tipo, UUID marca) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                id, tipo, marca);

        return id;
    }

    @Test
    @DisplayName("las seis tablas del catalogo existen tras la migracion")
    void migracionCreaLasTablas() {
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('fabricante', 'marca', 'tipo_equipo', 'equipo',
                                             'modelo', 'equipo_cliente')
                        """,
                        Integer.class))
                .isEqualTo(6);
    }

    @Test
    @DisplayName("una marca no puede quedarse sin nombre")
    void marcaSinNombre() {
        // En el original la columna era anulable, y el nombre es lo unico que una marca tiene.
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, NULL)",
                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el amperaje admite decimales y valores por encima de 99")
    void amperajeConDecimales() {
        // El original lo declaraba numeric(2): maximo 99 y sin decimales, de modo que 2.5 A se
        // redondeaba a 3.
        UUID tipo = insertType(false, null, new BigDecimal("2.50"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT d_amperaje FROM tipo_equipo WHERE k_id_tipo_equipo = ?",
                        BigDecimal.class, tipo))
                .isEqualByComparingTo("2.50");

        assertThatCode(() -> insertType(false, null, new BigDecimal("120.75")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un tipo verificable exige decir como se verifica")
    void verificableExigeModalidad() {
        assertThatCode(() -> insertType(true, "patron_constante", null)).doesNotThrowAnyException();

        // El original dejaba las dos columnas sueltas.
        assertThatThrownBy(() -> insertType(true, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un tipo no verificable no puede traer modalidad")
    void noVerificableSinModalidad() {
        assertThatThrownBy(() -> insertType(false, "patron_constante", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la modalidad de verificacion solo admite los tres valores del catalogo")
    void modalidadDelCatalogo() {
        assertThatThrownBy(() -> insertType(true, "a_ojo", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el valor del mantenimiento no puede ser negativo")
    void valorNoNegativo() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo,
                                                 t_definicion_tecnica, t_recomendaciones_cuidado,
                                                 t_tecnologia_predominante,
                                                 m_valor_unitario_mantenimiento)
                        VALUES (?, ?, 'D', 'C', 'E', -1)
                        """,
                        UUID.randomUUID(), "Tipo " + unico()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la misma marca no fabrica dos veces el mismo tipo de equipo")
    void asociacionUnica() {
        UUID tipo = insertType(false, null, null);
        UUID marca = insertBrand();
        insertEquipment(tipo, marca);

        // equipo es una asociacion: repetir el par no significa nada.
        assertThatThrownBy(() -> insertEquipment(tipo, marca))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un modelo necesita fabricante y equipo")
    void modeloNecesitaSusReferencias() {
        UUID equipo = insertEquipment(insertType(false, null, null), insertBrand());

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO modelo (k_id_modelo, k_id_fabricante, k_id_equipo) VALUES (?, NULL, ?)",
                        UUID.randomUUID(), equipo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el registro INVIMA no se repite, pero varios modelos pueden no tenerlo")
    void invimaUnicoPeroOpcional() {
        UUID fabricante = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                fabricante, "Fabricante " + unico());

        UUID equipo = insertEquipment(insertType(false, null, null), insertBrand());
        String invima = "INVIMA-" + unico();

        jdbcTemplate.update(
                "INSERT INTO modelo (k_id_modelo, n_invima, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), invima, fabricante, equipo);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO modelo (k_id_modelo, n_invima, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(), invima, fabricante, equipo))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Varios modelos sin registro conviven: Postgres admite nulos repetidos bajo UNIQUE.
        assertThatCode(() -> {
                    jdbcTemplate.update(
                            "INSERT INTO modelo (k_id_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?)",
                            UUID.randomUUID(), fabricante, equipo);
                    jdbcTemplate.update(
                            "INSERT INTO modelo (k_id_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?)",
                            UUID.randomUUID(), fabricante, equipo);
                })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("equipo_cliente ya existe: la pieza que V4__client dejo pendiente")
    void equipoClienteExiste() {
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'equipo_cliente'
                          AND column_name IN ('k_id_modelo', 'k_id_area_servicio')
                          AND is_nullable = 'NO'
                        """,
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("una modalidad constante exige decir cuantos datos se toman")
    void modalidadConstanteExigeCantidad() {
        // Sin esto cabe un tipo que dice comparar contra un patron constante sin decir cuantas
        // lecturas se toman, y el reporte no se puede llenar.
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo,
                                                 t_definicion_tecnica, t_recomendaciones_cuidado,
                                                 t_tecnologia_predominante, b_verificable,
                                                 n_tipo_verificacion, m_valor_unitario_mantenimiento)
                        VALUES (?, ?, 'D', 'C', 'E', true, 'patron_constante', 1000)
                        """,
                        UUID.randomUUID(), "Tipo " + unico()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la modalidad variable no admite cantidad: la decide el ingeniero en campo")
    void modalidadVariableProhibeCantidad() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo,
                                                 t_definicion_tecnica, t_recomendaciones_cuidado,
                                                 t_tecnologia_predominante, b_verificable,
                                                 n_tipo_verificacion, i_cantidad_datos,
                                                 m_valor_unitario_mantenimiento)
                        VALUES (?, ?, 'D', 'C', 'E', true, 'patron_equipo_variable', 5, 1000)
                        """,
                        UUID.randomUUID(), "Tipo " + unico()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un tipo que no se verifica tampoco lleva cantidad")
    void noVerificableProhibeCantidad() {
        // Es la rama que en SQL se escapa: «NOT IN (...)» con NULL da NULL, no falso.
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo,
                                                 t_definicion_tecnica, t_recomendaciones_cuidado,
                                                 t_tecnologia_predominante, b_verificable,
                                                 n_tipo_verificacion, i_cantidad_datos,
                                                 m_valor_unitario_mantenimiento)
                        VALUES (?, ?, 'D', 'C', 'E', false, NULL, 5, 1000)
                        """,
                        UUID.randomUUID(), "Tipo " + unico()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la cantidad de datos va de 1 a 100")
    void cantidadAcotada() {
        for (int cantidad : new int[] {0, 101}) {
            assertThatThrownBy(() -> jdbcTemplate.update(
                            """
                            INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo,
                                                     t_definicion_tecnica, t_recomendaciones_cuidado,
                                                     t_tecnologia_predominante, b_verificable,
                                                     n_tipo_verificacion, i_cantidad_datos,
                                                     m_valor_unitario_mantenimiento)
                            VALUES (?, ?, 'D', 'C', 'E', true, 'equipo_constante', ?, 1000)
                            """,
                            UUID.randomUUID(), "Tipo " + unico(), cantidad))
                    .describedAs("cantidad " + cantidad)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("un punto de verificacion admite valores negativos: un congelador se verifica a -20 grados")
    void puntoAdmiteNegativos() {
        // Un CHECK de positividad aqui habria dejado fuera media cadena de frio.
        UUID tipo = insertType(true, "patron_constante", null);

        assertThatCode(() -> insertPoint(tipo, "-20.0000", "°C", true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un punto necesita su tipo y una unidad que diga algo")
    void puntoNecesitaTipoYUnidad() {
        UUID tipo = insertType(true, "patron_constante", null);

        assertThatThrownBy(() -> insertPoint(UUID.randomUUID(), "100", "mmHg", true))
                .describedAs("un tipo que no existe")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPoint(tipo, "100", "   ", true))
                .describedAs("una unidad en blanco")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("dos puntos activos iguales son el mismo dos veces, pero uno retirado no estorba")
    void puntoActivoUnico() {
        UUID tipo = insertType(true, "patron_constante", null);
        insertPoint(tipo, "100.0000", "mmHg", true);

        assertThatThrownBy(() -> insertPoint(tipo, "100.0000", "mmHg", true))
                .isInstanceOf(DataIntegrityViolationException.class);

        // El indice es parcial: con uno retirado se puede volver a dar de alta el mismo valor, que es
        // lo que permite reconfigurar sin borrar nada.
        insertPoint(tipo, "50.0000", "mmHg", false);
        assertThatCode(() -> insertPoint(tipo, "50.0000", "mmHg", true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el mismo valor en otra unidad es otro punto")
    void puntoDistinguePorUnidad() {
        UUID tipo = insertType(true, "equipo_constante", null);
        insertPoint(tipo, "100.0000", "mmHg", true);

        assertThatCode(() -> insertPoint(tipo, "100.0000", "kPa", true)).doesNotThrowAnyException();
    }
}
