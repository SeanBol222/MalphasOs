package com.malphasos.malphasos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.infrastructure.output.entities.ServiceReportEntity;
import com.malphasos.malphasos.report.infrastructure.output.mapper.ServiceReportPersistenceMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * {@code V59}, que muda lo que el reporte dice a {@code t_contenido} (2026-11-19), sobre un esquema aparte en
 * {@code V58} con un reporte que tiene de todo: textos, ingreso, quien recibe, pasos, accesorios, patrones y
 * lecturas, con algo retirado en cada colección que lo admite.
 *
 * <p>La prueba que importa es la última: <b>el documento que escribe la migración es el mismo que escribe el
 * mapper</b>, clave por clave y en el mismo orden. Los dos escriben {@code t_contenido} —V59 los reportes que
 * ya estaban, Java los de ahora— y los dos los lee el mismo código.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReportContentMigrationTest {

    private static final String ESQUEMA = "prueba_v59";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ServiceReportPersistenceMapper mapper;

    private SingleConnectionDataSource conexion;
    private JdbcTemplate enElEsquema;

    private UUID reporte;
    private UUID verificacionConPuntos;
    private UUID verificacionVariable;
    private UUID punto;
    private UUID patron;
    private UUID protocoloHecho;
    private UUID protocoloNoAplica;

    @AfterEach
    void borrarEsquema() {
        if (conexion != null) {
            enElEsquema.execute("RESET search_path");
            conexion.destroy();
        }
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
    }

    private void migrarHasta(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(ESQUEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private void sql(String sentencia, Object... argumentos) {
        enElEsquema.update(sentencia, argumentos);
    }

    /** Un esquema en V58 con un reporte cerrado que lo tiene todo, y lo migra a V59. */
    private void unReporteCompletoMigrado() {
        migrarHasta("58");
        conexion = new SingleConnectionDataSource(DataSourceUtils.getConnection(dataSource), true);
        enElEsquema = new JdbcTemplate(conexion);
        enElEsquema.execute("SET search_path TO " + ESQUEMA);

        UUID ciudad = enElEsquema.queryForObject("SELECT k_id_ciudad FROM ciudad LIMIT 1", UUID.class);
        UUID cliente = UUID.randomUUID();
        sql("INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social, n_sigla)"
                + " VALUES (?, '9001', 'NIT_juridico', 'Hospital', 'HOS')", cliente);
        UUID sede = UUID.randomUUID();
        sql("INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad)"
                + " VALUES (?, 'Norte', '1', '2', '3', ?, ?)", sede, cliente, ciudad);
        UUID area = UUID.randomUUID();
        sql("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, 'Urgencias', ?)",
                area, sede);
        UUID tipo = UUID.randomUUID();
        sql("INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,"
                + " t_recomendaciones_cuidado, t_tecnologia_predominante, m_valor_unitario_mantenimiento)"
                + " VALUES (?, 'Monitor', 'D', 'C', 'T', 1000)", tipo);
        UUID marca = UUID.randomUUID();
        sql("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, 'Mindray')", marca);
        UUID equipo = UUID.randomUUID();
        sql("INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)", equipo, tipo, marca);
        UUID fabricante = UUID.randomUUID();
        sql("INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, 'Mindray Co')", fabricante);
        UUID modelo = UUID.randomUUID();
        sql("INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, 'M1', ?, ?)",
                modelo, fabricante, equipo);
        UUID unidad = UUID.randomUUID();
        sql("INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)"
                + " VALUES (?, 'S-1', ?, ?)", unidad, modelo, area);
        UUID orden = UUID.randomUUID();
        sql("INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad,"
                + " t_tipo_servicio, t_estado_ejecucion) VALUES (?, ?, ?, 'MENSUAL', 'CORRECTIVO', 'EN_EJECUCION')",
                orden, sede, LocalDate.of(2026, 9, 1));

        verificacionConPuntos = UUID.randomUUID();
        sql("INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo, k_id_unidad_medida,"
                + " n_modalidad_verificacion, i_cantidad_datos, d_tolerancia) SELECT ?, ?, k_id_unidad_medida,"
                + " 'patron_constante', 2, 5 FROM unidad_medida WHERE n_simbolo_unidad = 'mmHg'",
                verificacionConPuntos, tipo);
        punto = UUID.randomUUID();
        sql("INSERT INTO punto_verificacion (k_id_punto_verificacion, k_id_verificacion, d_valor) VALUES (?, ?, 100)",
                punto, verificacionConPuntos);
        verificacionVariable = UUID.randomUUID();
        sql("INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo, k_id_unidad_medida,"
                + " n_modalidad_verificacion, d_tolerancia) SELECT ?, ?, k_id_unidad_medida,"
                + " 'patron_equipo_variable', 1 FROM unidad_medida WHERE n_simbolo_unidad = 'lpm'",
                verificacionVariable, tipo);
        patron = UUID.randomUUID();
        sql("INSERT INTO equipo_patron (k_id_equipo_patron, n_nombre, k_serie, n_certificado,"
                + " f_vencimiento_certificado) VALUES (?, 'Simulador', 'P-1', 'C-1', ?)", patron,
                LocalDate.of(2027, 1, 1));
        UUID otroPatron = UUID.randomUUID();
        sql("INSERT INTO equipo_patron (k_id_equipo_patron, n_nombre, k_serie, n_certificado,"
                + " f_vencimiento_certificado) VALUES (?, 'Otro', 'P-2', 'C-2', ?)", otroPatron,
                LocalDate.of(2027, 1, 1));
        protocoloHecho = UUID.randomUUID();
        protocoloNoAplica = UUID.randomUUID();
        sql("INSERT INTO protocolo (k_id_protocolo, n_nombre, n_clave, t_descripcion) VALUES (?, 'Limpieza',"
                + " 'limpieza', 'Limpieza general'), (?, 'Pinza', 'pinza', 'Revisar la pinza')",
                protocoloHecho, protocoloNoAplica);

        reporte = UUID.randomUUID();
        sql("""
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                    t_estado_reporte, t_falla_reportada, t_diagnostico, t_causa, t_repuestos, t_procedimientos,
                    t_observaciones, t_resultado, f_finalizado, t_ingreso_fisico, t_ingreso_fisico_observacion,
                    t_ingreso_operativo, n_recibe_nombre, k_recibe_cedula, n_recibe_cargo)
                VALUES (?, ?, ?, 'BORRADOR', 'No enciende', 'Fuente quemada', 'Sobretension', 'Fuente 12 V',
                    'Cambio de fuente', 'Sin novedad', NULL, NULL, 'NO_CONFORME', 'Carcasa rayada', 'CONFORME',
                    'Ana Ruiz', '1234', 'Jefe de enfermeria')
                """, reporte, orden, unidad);

        // Lecturas: dos vigentes en el punto, una corregida (retirada) y una vigente sin punto.
        sql("INSERT INTO dato_verificacion (k_id_dato_verificacion, k_id_reporte_servicio, k_id_verificacion,"
                        + " k_id_punto_verificacion, i_secuencia, d_valor_patron, d_valor_equipo, b_estado_activo)"
                        + " VALUES (?, ?, ?, ?, 1, 100, 95, false), (?, ?, ?, ?, 1, 100, 96, true),"
                        + " (?, ?, ?, ?, 2, 100, 101.5, true), (?, ?, ?, NULL, 1, 72.25, 73, true)",
                UUID.randomUUID(), reporte, verificacionConPuntos, punto,
                UUID.randomUUID(), reporte, verificacionConPuntos, punto,
                UUID.randomUUID(), reporte, verificacionConPuntos, punto,
                UUID.randomUUID(), reporte, verificacionVariable);
        // Patrones: el simulador en las dos verificaciones, y otro que se quitó.
        sql("INSERT INTO patron_verificacion_reporte (k_id_patron_verificacion_reporte, k_id_reporte_servicio,"
                        + " k_id_verificacion, k_id_equipo_patron, b_estado_activo)"
                        + " VALUES (?, ?, ?, ?, true), (?, ?, ?, ?, true), (?, ?, ?, ?, false)",
                UUID.randomUUID(), reporte, verificacionConPuntos, patron,
                UUID.randomUUID(), reporte, verificacionVariable, patron,
                UUID.randomUUID(), reporte, verificacionConPuntos, otroPatron);
        sql("INSERT INTO paso_reporte (k_id_paso_reporte, k_id_reporte_servicio, k_id_protocolo, t_estado_paso,"
                        + " t_nota) VALUES (?, ?, ?, 'HECHO', NULL), (?, ?, ?, 'NO_APLICA', 'No tiene pinza')",
                UUID.randomUUID(), reporte, protocoloHecho, UUID.randomUUID(), reporte, protocoloNoAplica);
        // Accesorios: el segundo y el tercero en orden, y uno retirado en medio.
        sql("INSERT INTO accesorio_reporte (k_id_accesorio_reporte, k_id_reporte_servicio, i_orden, n_nombre,"
                        + " b_conforme, t_observacion, b_estado_activo) VALUES (?, ?, 1, 'Cable', true, NULL, true),"
                        + " (?, ?, 2, 'Viejo', true, NULL, false), (?, ?, 2, 'Brazalete', false, 'Roto', true)",
                UUID.randomUUID(), reporte, UUID.randomUUID(), reporte, UUID.randomUUID(), reporte);

        migrarHasta("59");
    }

    private String contenido(String ruta) {
        return enElEsquema.queryForObject(
                "SELECT t_contenido #>> ?::text[] FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                String.class, ruta, reporte);
    }

    @Test
    @DisplayName("los textos, el ingreso y quien recibe pasan al documento tal cual")
    void losTextos() {
        unReporteCompletoMigrado();

        assertThat(contenido("{version}")).isEqualTo("1");
        assertThat(contenido("{falla}")).isEqualTo("No enciende");
        assertThat(contenido("{diagnostico}")).isEqualTo("Fuente quemada");
        assertThat(contenido("{causa}")).isEqualTo("Sobretension");
        assertThat(contenido("{repuestos}")).isEqualTo("Fuente 12 V");
        assertThat(contenido("{procedimientos}")).isEqualTo("Cambio de fuente");
        assertThat(contenido("{observaciones}")).isEqualTo("Sin novedad");
        assertThat(contenido("{ingreso,fisico,estado}")).isEqualTo("NO_CONFORME");
        assertThat(contenido("{ingreso,fisico,observacion}")).isEqualTo("Carcasa rayada");
        assertThat(contenido("{ingreso,operativo,estado}")).isEqualTo("CONFORME");
        assertThat(contenido("{ingreso,operativo,observacion}")).isNull();
        assertThat(contenido("{recibe,nombre}")).isEqualTo("Ana Ruiz");
        assertThat(contenido("{recibe,cedula}")).isEqualTo("1234");
        assertThat(contenido("{recibe,cargo}")).isEqualTo("Jefe de enfermeria");
    }

    @Test
    @DisplayName("solo pasa lo vigente: la lectura corregida, el patron quitado y el accesorio retirado no")
    void soloLoVigente() {
        unReporteCompletoMigrado();

        assertThat(enElEsquema.queryForObject("""
                SELECT count(*) FROM reporte_servicio, jsonb_array_elements(t_contenido -> 'verificaciones') v,
                    jsonb_array_elements(v -> 'lecturas') WHERE k_id_reporte_servicio = ?
                """, Integer.class, reporte)).isEqualTo(3);
        assertThat(enElEsquema.queryForObject("""
                SELECT count(*) FROM reporte_servicio, jsonb_array_elements(t_contenido -> 'verificaciones') v,
                    jsonb_array_elements(v -> 'patrones') WHERE k_id_reporte_servicio = ?
                """, Integer.class, reporte)).isEqualTo(2);
        assertThat(contenido("{accesorios,0,nombre}")).isEqualTo("Cable");
        assertThat(contenido("{accesorios,1,nombre}")).isEqualTo("Brazalete");
        assertThat(contenido("{accesorios,1,observacion}")).isEqualTo("Roto");
        assertThat(contenido("{accesorios,2}")).isNull();
        // Y la lectura que vale en el punto, secuencia 1, es la corregida.
        assertThat(enElEsquema.queryForObject("""
                SELECT (l ->> 'equipo')::numeric FROM reporte_servicio,
                    jsonb_array_elements(t_contenido -> 'verificaciones') v, jsonb_array_elements(v -> 'lecturas') l
                WHERE k_id_reporte_servicio = ? AND (l ->> 'punto')::uuid = ? AND (l ->> 'secuencia')::int = 1
                """, java.math.BigDecimal.class, reporte, punto)).isEqualByComparingTo("96");
    }

    @Test
    @DisplayName("salen las cuatro tablas y las trece columnas, y la fila conserva las suyas")
    void loQueSale() {
        unReporteCompletoMigrado();

        assertThat(enElEsquema.queryForObject("""
                SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name IN
                    ('dato_verificacion', 'patron_verificacion_reporte', 'paso_reporte', 'accesorio_reporte')
                """, Integer.class, ESQUEMA)).isZero();
        assertThat(enElEsquema.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_schema = ?
                    AND table_name = 'reporte_servicio' ORDER BY ordinal_position
                """, String.class, ESQUEMA))
                .containsExactly("k_id_reporte_servicio", "k_id_orden_trabajo", "k_id_equipo_cliente",
                        "t_estado_reporte", "t_resultado", "f_finalizado", "b_estado_activo", "t_documento",
                        "b_documento_tardio", "k_huella_firma_recibe", "f_firma_recibe", "k_id_recibe",
                        "t_contenido");
    }

    @Test
    @DisplayName("el documento no admite perder un arreglo ni una version que no es numero")
    void laFormaDelDocumento() {
        unReporteCompletoMigrado();

        assertThatThrownBy(() -> sql("UPDATE reporte_servicio SET t_contenido = t_contenido - 'pasos'"))
                .hasMessageContaining("CHK_reporte_servicio_contenido");
        assertThatThrownBy(() -> sql("UPDATE reporte_servicio SET t_contenido = t_contenido - 'verificaciones'"))
                .hasMessageContaining("CHK_reporte_servicio_contenido");
        assertThatThrownBy(() -> sql(
                        "UPDATE reporte_servicio SET t_contenido = jsonb_set(t_contenido, '{version}', '\"1\"')"))
                .hasMessageContaining("CHK_reporte_servicio_contenido");
    }

    @Test
    @DisplayName("una firma necesita su fecha y a alguien que la diera, aunque falte la clave recibe")
    void laFirma() {
        unReporteCompletoMigrado();
        String firma = "a".repeat(64);
        sql("INSERT INTO imagen_documento (k_huella, b_contenido, t_tipo) VALUES (?, '\\x89'::bytea, 'image/png')",
                firma);

        assertThatThrownBy(() -> sql("UPDATE reporte_servicio SET k_huella_firma_recibe = ?", firma))
                .hasMessageContaining("CHK_reporte_servicio_firma_recibe");
        assertThatThrownBy(() -> sql("UPDATE reporte_servicio SET k_huella_firma_recibe = ?, f_firma_recibe = ?,"
                        + " t_contenido = t_contenido - 'recibe'", firma, LocalDateTime.now()))
                .hasMessageContaining("CHK_reporte_servicio_firma_recibe");
        // Con fecha y con el nombre escrito de quien recibe, entra.
        sql("UPDATE reporte_servicio SET k_huella_firma_recibe = ?, f_firma_recibe = ?", firma, LocalDateTime.now());
    }

    @Test
    @DisplayName("el documento de la migracion es el mismo que escribe el mapper, clave por clave y en orden")
    void elMismoQueJava() {
        unReporteCompletoMigrado();
        String deLaMigracion = enElEsquema.queryForObject(
                "SELECT t_contenido::text FROM reporte_servicio WHERE k_id_reporte_servicio = ?", String.class,
                reporte);

        ServiceReportEntity fila = new ServiceReportEntity();
        fila.setId(reporte);
        fila.setIdOrdenTrabajo(UUID.randomUUID());
        fila.setIdEquipoCliente(UUID.randomUUID());
        fila.setEstado("BORRADOR");
        fila.setEstadoActivo(true);
        fila.setContenido(deLaMigracion);
        ServiceReport leido = mapper.toDomain(fila);
        String deJava = mapper.toEntity(leido, null).getContenido();

        // jsonb compara por valor y conserva el orden de los arreglos: igual significa lo mismo, en el mismo
        // orden. Las claves de un objeto no tienen orden en jsonb, y no hace falta que lo tengan.
        assertThat(enElEsquema.queryForObject("SELECT ?::jsonb = ?::jsonb", Boolean.class, deLaMigracion, deJava))
                .as("migracion: %s%njava: %s", deLaMigracion, deJava)
                .isTrue();
    }
}
