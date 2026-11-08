package com.malphasos.malphasos.dataload;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Los países y la ciudad que nombran los ejemplos de carga, creados si faltan. Las pruebas no se apoyan en
 * la siembra de {@code V7}: otras clases borran {@code pais} y {@code ciudad}, y una prueba que la usa pasa
 * sola y falla en la batería.
 */
public final class UbicacionDelEjemplo {

    private UbicacionDelEjemplo() {}

    public static void crear(JdbcTemplate jdbcTemplate) {
        for (String[] pais : new String[][] {{"USA", "Estados Unidos"}, {"COL", "Colombia"}}) {
            jdbcTemplate.update("""
                    INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais)
                    SELECT gen_random_uuid(), ?, ? WHERE NOT EXISTS (SELECT 1 FROM pais WHERE k_codigo_iso = ?)""",
                    pais[0], pais[1], pais[0]);
        }
        jdbcTemplate.update("""
                INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais)
                SELECT gen_random_uuid(), 'Bogotá', k_id_pais FROM pais WHERE k_codigo_iso = 'COL'
                AND NOT EXISTS (SELECT 1 FROM ciudad c JOIN pais p ON p.k_id_pais = c.k_id_pais
                                WHERE p.k_codigo_iso = 'COL' AND c.n_nombre_ciudad = 'Bogotá')""");
    }
}
