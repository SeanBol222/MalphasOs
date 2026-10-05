package com.malphasos.malphasos;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Un código ISO de país que no esté ya en la base, para las pruebas que necesitan crear un país.
 *
 * <p><b>Existe porque diez clases de prueba compartían una intermitencia que nadie había anotado.</b>
 * Inventaban el código con tres letras sacadas de {@code nanoTime()}, sin mirar si existía, y
 * {@code V7} siembra los <b>249 países reales</b> de la ISO: 249 de 17.576 combinaciones es un 1,4 %
 * de choque por llamada, y una clase con quince pruebas que crean un país cada una fallaba una de
 * cada tres ejecuciones. Se destapó el 2026-10-04 cuando le tocó {@code MDA}, que es Moldavia.
 *
 * <p>Es la <b>tercera causa</b> de que la batería diera resultados distintos sin cambiar nada. Las dos
 * anteriores —el {@code unico()} que recortaba {@code nanoTime()} y la comprobación de salud de
 * RabbitMQ— están en el {@code CONVENCIONES.md} de la raíz; esta no estaba.
 *
 * <p><b>Por qué se comprueba y no se reduce el rango.</b> Los códigos {@code X??} son de uso privado
 * y la ISO no los asigna nunca, pero las pruebas <b>no borran los países que crean</b>, de modo que
 * en un rango de 676 códigos chocarían entre ellas mismas en cuanto llevaran unas decenas. Mirar
 * antes de usar elimina las dos causas a la vez, y con tan pocos ocupados termina al primer intento
 * casi siempre.
 */
public final class CodigoIsoLibre {

    private static final String LETRAS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private CodigoIsoLibre() {
    }

    public static String en(JdbcTemplate jdbcTemplate) {
        while (true) {
            ThreadLocalRandom azar = ThreadLocalRandom.current();
            String codigo = "" + LETRAS.charAt(azar.nextInt(26))
                    + LETRAS.charAt(azar.nextInt(26))
                    + LETRAS.charAt(azar.nextInt(26));

            Integer ocupado = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pais WHERE k_codigo_iso = ?", Integer.class, codigo);

            if (ocupado == 0) {
                return codigo;
            }
        }
    }
}
