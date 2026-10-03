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
            "DELETE FROM verificacion_tipo_equipo",
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

    /**
     * Un tipo de equipo. <b>Ya no recibe modalidad ni verificable</b>: las tres columnas bajaron a
     * {@code verificacion_tipo_equipo} en {@code V10}, y «se verifica» es «tiene filas allí».
     */
    private UUID insertType(BigDecimal amperaje) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         d_amperaje, m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', ?, 150000)
                """,
                id, "Tipo " + unico(), amperaje);

        return id;
    }

    /** La magnitud sembrada por {@code V10} con ese código. No se inventa: el esquema la exige real. */
    private UUID magnitud(String codigo) {
        return jdbcTemplate.queryForObject(
                "SELECT k_id_magnitud FROM magnitud WHERE n_codigo_magnitud = ?", UUID.class, codigo);
    }

    /** Una unidad de esa magnitud. El par (magnitud, unidad) es lo que la foránea compuesta exige. */
    private UUID unidad(String codigoMagnitud, String simbolo) {
        return jdbcTemplate.queryForObject(
                """
                SELECT u.k_id_unidad_medida FROM unidad_medida u
                         JOIN magnitud m USING (k_id_magnitud)
                WHERE m.n_codigo_magnitud = ? AND u.n_simbolo_unidad = ?
                """,
                UUID.class, codigoMagnitud, simbolo);
    }

    private UUID insertVerification(
            UUID tipo, String codigoMagnitud, String simbolo, String modalidad, Integer cantidad) {

        return insertVerification(tipo, codigoMagnitud, simbolo, modalidad, cantidad, true);
    }

    private UUID insertVerification(UUID tipo, String codigoMagnitud, String simbolo,
            String modalidad, Integer cantidad, boolean activa) {

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo,
                                                      k_id_magnitud, k_id_unidad_medida,
                                                      n_modalidad_verificacion, i_cantidad_datos,
                                                      b_estado_activo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, tipo, magnitud(codigoMagnitud), unidad(codigoMagnitud, simbolo), modalidad,
                cantidad, activa);

        return id;
    }

    /** Una verificación constante con lo que su modalidad exige, para colgarle puntos. */
    private UUID unaVerificacionConstante(UUID tipo) {
        return insertVerification(tipo, "presion", "mmHg", "patron_constante", 3);
    }

    /** Un punto. <b>Ya no lleva unidad</b>: la declara su verificación. */
    private UUID insertPoint(UUID verificacion, String valor, boolean activo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO punto_verificacion (k_id_punto_verificacion, k_id_verificacion, d_valor,
                                                b_estado_activo)
                VALUES (?, ?, CAST(? AS numeric), ?)
                """,
                id, verificacion, valor, activo);

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
    @DisplayName("las nueve tablas del catalogo existen tras las migraciones")
    void migracionCreaLasTablas() {
        // Eran seis hasta V8, siete con punto_verificacion, y nueve desde V10 con el nivel de las
        // verificaciones y los dos catalogos metrologicos.
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('fabricante', 'marca', 'tipo_equipo', 'equipo',
                                             'modelo', 'equipo_cliente', 'verificacion_tipo_equipo',
                                             'magnitud', 'unidad_medida')
                        """,
                        Integer.class))
                .isEqualTo(9);
    }

    @Test
    @DisplayName("tipo_equipo ya no lleva las tres columnas que bajaron de nivel")
    void tipoSinLasColumnasViejas() {
        // b_verificable era exactamente 'n_tipo_verificacion IS NOT NULL', redundante por
        // construccion, y las otras dos no pueden ser del tipo porque valen distinto por magnitud.
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'tipo_equipo'
                          AND column_name IN ('b_verificable', 'n_tipo_verificacion',
                                              'i_cantidad_datos')
                        """,
                        Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("el catalogo metrologico esta sembrado, y un simbolo puede estar en dos magnitudes")
    void catalogoSembrado() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM magnitud", Integer.class))
                .isGreaterThanOrEqualTo(20);
        // '%' es a la vez humedad relativa y concentracion: por eso el simbolo es unico POR magnitud
        // y no en toda la tabla. Una unicidad global habria obligado a inventarse un simbolo falso.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM unidad_medida WHERE n_simbolo_unidad = '%'",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("una unidad de otra magnitud no se puede declarar: lo impide la foranea compuesta")
    void unidadDeOtraMagnitud() {
        // Es la regla que el esquema SI puede expresar, y la expresa: la foranea apunta al par
        // (magnitud, unidad) a la vez, de modo que %HR para temperatura es imposible de escribir.
        UUID tipo = insertType(null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo,
                                                              k_id_magnitud, k_id_unidad_medida,
                                                              n_modalidad_verificacion,
                                                              i_cantidad_datos)
                        VALUES (?, ?, ?, ?, 'patron_constante', 3)
                        """,
                        UUID.randomUUID(), tipo, magnitud("temperatura"),
                        unidad("humedad_relativa", "%HR")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un tipo no declara dos veces la misma magnitud, pero una retirada no estorba")
    void magnitudUnicaPorTipo() {
        UUID tipo = insertType(null);
        insertVerification(tipo, "presion", "mmHg", "patron_constante", 3);

        assertThatThrownBy(() -> insertVerification(tipo, "presion", "kPa", "equipo_constante", 1))
                .describedAs("la misma magnitud, otra unidad, las dos activas")
                .isInstanceOf(DataIntegrityViolationException.class);

        // El indice es parcial: una retirada deja volver a declarar su magnitud, que es lo que permite
        // reconfigurar sin borrar nada.
        insertVerification(tipo, "temperatura", "°C", "patron_constante", 3, false);
        assertThatCode(() -> insertVerification(tipo, "temperatura", "K", "equipo_constante", 1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dos magnitudes distintas en el mismo tipo si valen: es el termohigrometro")
    void dosMagnitudesEnElMismoTipo() {
        UUID tipo = insertType(null);
        insertVerification(tipo, "temperatura", "°C", "patron_constante", 3);

        assertThatCode(() -> insertVerification(
                        tipo, "humedad_relativa", "%HR", "patron_equipo_variable", null))
                .doesNotThrowAnyException();
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
        UUID tipo = insertType(new BigDecimal("2.50"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT d_amperaje FROM tipo_equipo WHERE k_id_tipo_equipo = ?",
                        BigDecimal.class, tipo))
                .isEqualByComparingTo("2.50");

        assertThatCode(() -> insertType(new BigDecimal("120.75")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una verificacion necesita su modalidad, y del catalogo de tres")
    void modalidadObligatoriaYDelCatalogo() {
        // Antes la modalidad del TIPO podia ser nula y significaba 'no se verifica'. Ahora eso se dice
        // con la ausencia de filas, y una verificacion sin modalidad no tiene sentido.
        UUID tipo = insertType(null);

        assertThatThrownBy(() -> insertVerification(tipo, "presion", "mmHg", null, 3))
                .describedAs("sin modalidad")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertVerification(tipo, "presion", "mmHg", "a_ojo", 3))
                .describedAs("una modalidad inventada")
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
        UUID tipo = insertType(null);
        UUID marca = insertBrand();
        insertEquipment(tipo, marca);

        // equipo es una asociacion: repetir el par no significa nada.
        assertThatThrownBy(() -> insertEquipment(tipo, marca))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un modelo necesita fabricante y equipo")
    void modeloNecesitaSusReferencias() {
        UUID equipo = insertEquipment(insertType(null), insertBrand());

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

        UUID equipo = insertEquipment(insertType(null), insertBrand());
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
        // Sin esto cabe una verificacion que dice comparar contra un patron constante sin decir
        // cuantas lecturas se toman, y el reporte no se puede llenar.
        UUID tipo = insertType(null);

        assertThatThrownBy(() -> insertVerification(tipo, "presion", "mmHg", "patron_constante", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la modalidad variable no admite cantidad: la decide el ingeniero en campo")
    void modalidadVariableProhibeCantidad() {
        UUID tipo = insertType(null);

        assertThatThrownBy(() -> insertVerification(
                        tipo, "presion", "mmHg", "patron_equipo_variable", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la cantidad de datos va de 1 a 100")
    void cantidadAcotada() {
        UUID tipo = insertType(null);

        for (int cantidad : new int[] {0, 101}) {
            assertThatThrownBy(() -> insertVerification(
                            tipo, "presion", "mmHg", "equipo_constante", cantidad))
                    .describedAs("cantidad " + cantidad)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("un punto de verificacion admite valores negativos: un congelador se verifica a -20 grados")
    void puntoAdmiteNegativos() {
        // Un CHECK de positividad aqui habria dejado fuera media cadena de frio.
        UUID verificacion = unaVerificacionConstante(insertType(null));

        assertThatCode(() -> insertPoint(verificacion, "-20.0000", true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un punto necesita su verificacion, no su tipo de equipo")
    void puntoNecesitaSuVerificacion() {
        // Cambio de padre en V10: un punto de 50 no significa nada suelto en un aparato que mide
        // presion Y temperatura.
        assertThatThrownBy(() -> insertPoint(UUID.randomUUID(), "100", true))
                .describedAs("una verificacion que no existe")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("punto_verificacion ya no tiene columna de unidad")
    void puntoSinUnidad() {
        // Era lo que obligaba a teclear 'mmHg' tantas veces como puntos hubiera, y permitia que dos
        // puntos hermanos se contradijeran.
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'punto_verificacion' AND column_name = 'n_unidad'
                        """,
                        Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("dos puntos activos iguales son el mismo dos veces, pero uno retirado no estorba")
    void puntoActivoUnico() {
        UUID verificacion = unaVerificacionConstante(insertType(null));
        insertPoint(verificacion, "100.0000", true);

        assertThatThrownBy(() -> insertPoint(verificacion, "100.0000", true))
                .isInstanceOf(DataIntegrityViolationException.class);

        // El indice es parcial: con uno retirado se puede volver a dar de alta el mismo valor, que es
        // lo que permite reconfigurar sin borrar nada.
        insertPoint(verificacion, "50.0000", false);
        assertThatCode(() -> insertPoint(verificacion, "50.0000", true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el mismo valor en otra verificacion es otro punto")
    void puntoDistinguePorVerificacion() {
        // Antes se distinguian por la unidad escrita a mano en el punto; ahora por su verificacion,
        // que es lo que hace que 40 grados y 40 por ciento no choquen.
        UUID tipo = insertType(null);
        UUID dePresion = insertVerification(tipo, "presion", "mmHg", "patron_constante", 3);
        UUID deTemperatura = insertVerification(tipo, "temperatura", "°C", "equipo_constante", 1);
        insertPoint(dePresion, "100.0000", true);

        assertThatCode(() -> insertPoint(deTemperatura, "100.0000", true))
                .doesNotThrowAnyException();
    }
}
