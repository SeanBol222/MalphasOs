package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentOverviewPersistencePort;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * El listado de equipos en SQL, y no con JPA, porque es una proyección y no un agregado: cinco tablas
 * de este módulo y la última intervención de cada equipo, filtradas y paginadas en la base. Con JPA
 * serían cinco repositorios y el filtrado en memoria, que es justo lo que este listado viene a
 * quitar del navegador.
 *
 * <p><b>El último servicio es la intervención vigente más reciente.</b> Las reemplazadas no cuentan
 * —{@code V14} las deja inactivas—, de modo que corregir un reporte no deja el resultado viejo a la
 * vista. Un equipo sin intervenciones sale con fecha y resultado nulos, y filtrar por resultado lo
 * deja fuera.
 */
@Component
@RequiredArgsConstructor
public class EquipmentOverviewPersistenceAdapter implements EquipmentOverviewPersistencePort {

    private static final String DESDE = """
            FROM equipo_cliente u
            JOIN modelo m ON m.k_id_modelo = u.k_id_modelo
            JOIN equipo e ON e.k_id_equipo = m.k_id_equipo
            JOIN tipo_equipo t ON t.k_id_tipo_equipo = e.k_id_tipo_equipo
            JOIN marca b ON b.k_id_marca = e.k_id_marca
            LEFT JOIN LATERAL (
                SELECT i.f_fecha_servicio, i.t_resultado
                FROM intervencion i
                WHERE i.k_id_equipo_cliente = u.k_id_equipo_cliente AND i.b_estado_activo
                ORDER BY i.f_fecha_servicio DESC
                LIMIT 1
            ) ult ON true
            """;

    /** Sin tildes ni mayúsculas, en SQL. Su gemela en Java es {@link #sinTildes}. */
    private static final String NORMALIZAR = "translate(lower(%s), 'áéíóúüñ', 'aeiouun')";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public EquipmentOverview find(EquipmentOverviewQuery consulta, Set<UUID> areas) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String donde = donde(consulta, areas, parametros);

        Long total = jdbc.queryForObject("SELECT count(*) " + DESDE + donde, parametros, Long.class);

        parametros.addValue("limite", consulta.tamano());
        parametros.addValue("desplazamiento", (long) consulta.pagina() * consulta.tamano());
        List<EquipmentOverview.Row> filas = jdbc.query(
                """
                SELECT u.k_id_equipo_cliente, u.n_numero_hoja_vida, u.k_serie, u.b_estado_activo,
                       u.k_id_area_servicio, t.k_id_tipo_equipo, t.n_nombre_tipo_equipo,
                       b.n_nombre_marca, m.n_nombre_modelo, ult.f_fecha_servicio, ult.t_resultado
                """ + DESDE + donde + """
                ORDER BY u.n_numero_hoja_vida NULLS LAST, u.k_serie, u.k_id_equipo_cliente
                LIMIT :limite OFFSET :desplazamiento
                """,
                parametros,
                (fila, numero) -> fila(fila));

        return new EquipmentOverview(filas, total == null ? 0 : total, consulta.pagina(), consulta.tamano());
    }

    private static String donde(EquipmentOverviewQuery consulta, Set<UUID> areas, MapSqlParameterSource parametros) {
        List<String> condiciones = new ArrayList<>();

        condiciones.add("u.b_estado_activo = :activos");
        parametros.addValue("activos", !consulta.dadosDeBaja());

        if (areas != null) {
            condiciones.add("u.k_id_area_servicio IN (:areas)");
            parametros.addValue("areas", areas);
        }

        if (consulta.idTipoEquipo() != null) {
            condiciones.add("t.k_id_tipo_equipo = :tipo");
            parametros.addValue("tipo", consulta.idTipoEquipo());
        }

        if (consulta.resultado() != null) {
            condiciones.add("ult.t_resultado = :resultado");
            parametros.addValue("resultado", consulta.resultado().name());
        }

        if (consulta.texto() != null) {
            condiciones.add(List.of("u.k_serie", "u.n_numero_hoja_vida", "m.n_nombre_modelo",
                            "t.n_nombre_tipo_equipo", "b.n_nombre_marca").stream()
                    .map(columna -> NORMALIZAR.formatted(columna) + " LIKE :texto ESCAPE '\\'")
                    .reduce((a, b) -> a + " OR " + b)
                    .map(o -> "(" + o + ")")
                    .orElseThrow());
            parametros.addValue("texto", "%" + escaparLike(sinTildes(consulta.texto())) + "%");
        }

        return "WHERE " + String.join(" AND ", condiciones) + "\n";
    }

    private static EquipmentOverview.Row fila(ResultSet fila) throws SQLException {
        Timestamp fecha = fila.getTimestamp("f_fecha_servicio");
        String resultado = fila.getString("t_resultado");

        return new EquipmentOverview.Row(
                fila.getObject("k_id_equipo_cliente", UUID.class),
                fila.getString("n_numero_hoja_vida"),
                fila.getString("k_serie"),
                fila.getObject("k_id_tipo_equipo", UUID.class),
                fila.getString("n_nombre_tipo_equipo"),
                fila.getString("n_nombre_marca"),
                fila.getString("n_nombre_modelo"),
                fila.getBoolean("b_estado_activo"),
                fila.getObject("k_id_area_servicio", UUID.class),
                fecha == null ? null : fecha.toLocalDateTime(),
                resultado == null ? null : InterventionResult.valueOf(resultado),
                null);
    }

    static String sinTildes(String texto) {
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    /** Lo que escribe quien busca es texto, no un patrón: «50%» busca un cincuenta por ciento. */
    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
