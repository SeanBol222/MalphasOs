package com.malphasos.malphasos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Toda foránea del esquema encabeza algún índice (V42, 2026-10-27).
 *
 * <p>Antes de V42 había trece sin ninguno, y nada lo decía: un índice que falta no rompe ninguna prueba,
 * solo hace lenta una consulta el día que la tabla crece. Esta prueba lo convierte en una regla que se
 * ve fallar al añadir una foránea sin su índice.
 *
 * <p>Cuenta como cubierta la foránea cuyas columnas son las primeras de un índice, en cualquier orden,
 * <b>aunque el índice sea parcial</b>: aquí nunca se borra una fila, así que el índice de una foránea
 * solo sirve para leer, y lo que se lee son las filas activas que ese índice parcial cubre.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ForeignKeyIndexContractTest {

    /**
     * Las que se quitan o se sustituyen en las migraciones siguientes de la refactorización, y por eso
     * V42 no las indexa. Cada migración que quita una la saca de aquí, y la prueba exige que sigan
     * existiendo: una excepción que ya no hace falta tiene que desaparecer de la lista. (La compuesta de
     * la orden con su cliente salió con V47, y la prueba lo exigió fallando hasta quitarla de aquí.)
     */
    private static final Set<String> SE_RETIRAN = Set.of(
            "intervencion.FK_intervencion_reemplazada_por",
            "verificacion_tipo_equipo.FK_verificacion_tipo_equipo_unidad_de_su_magnitud");

    @Autowired private JdbcTemplate jdbcTemplate;

    private List<String> foraneasSinIndice() {
        return jdbcTemplate.queryForList(
                """
                SELECT c.conrelid::regclass || '.' || c.conname
                FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE c.contype = 'f' AND n.nspname = current_schema()
                  AND NOT EXISTS (
                      SELECT 1 FROM pg_index i
                      WHERE i.indrelid = c.conrelid
                        AND (i.indkey::int2[])[0:array_length(c.conkey, 1) - 1] @> c.conkey
                        AND c.conkey @> (i.indkey::int2[])[0:array_length(c.conkey, 1) - 1])
                ORDER BY 1
                """,
                String.class);
    }

    @Test
    @DisplayName("ninguna foranea queda sin indice, salvo las que la refactorizacion va a quitar")
    void todaForaneaTieneIndice() {
        assertThat(foraneasSinIndice()).containsExactlyInAnyOrderElementsOf(SE_RETIRAN);
    }
}
