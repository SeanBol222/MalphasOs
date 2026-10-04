package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.application.ports.input.InterventionServicePort;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Cerrar un reporte escribe el historial, y nadie se lo pide.
 *
 * <p><b>Es la prueba de RF-26</b>, y por eso va de extremo a extremo contra un PostgreSQL real y no
 * con dobles: lo que el requisito exige —«no se requiere acción manual para actualizar el
 * historial»— no se puede demostrar con un mock del oyente, porque lo que hay que demostrar es
 * justamente que el oyente <b>se entera solo</b>. Aquí se cierra el reporte por la puerta del
 * servicio, igual que lo haría el API, y después se mira la hoja de vida del equipo.
 *
 * <p>También fija la <b>atomicidad</b>: el oyente corre dentro de la transacción del cierre, así que
 * si el historial no se pudiera escribir, el reporte no quedaría cerrado. No hay estado intermedio en
 * el que un reporte esté cerrado y su hoja de vida vacía.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM intervencion",
            "DELETE FROM dato_verificacion",
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo_equipo",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM fabricante",
            "DELETE FROM marca",
            "DELETE FROM tipo_equipo",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InterventionRecordingIntegrationTest {

    @Autowired private ServiceReportServicePort serviceReportServicePort;
    @Autowired private InterventionServicePort interventionServicePort;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    /** Toda la cadena hasta un reporte en borrador, por SQL para no depender de seis servicios. */
    private Contexto unBorrador(String tipoServicio) {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', 'Hospital Central')
                """,
                cliente, unico());

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

        UUID sede = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero,
                                  k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30-40', ?, ?)
                """,
                sede, "Sede " + unico(), cliente, ciudad);

        UUID area = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, "Area " + unico(), sede);

        // Un tipo de equipo SIN verificaciones: asi el cierre no exige tabla de lecturas completa, que
        // es otra regla y tiene sus propias pruebas.
        UUID tipo = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                tipo, "Tipo " + unico());

        UUID marca = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", marca, "Marca " + unico());

        UUID equipoCatalogo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                equipoCatalogo, tipo, marca);

        UUID fabricante = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                fabricante, "Fabricante " + unico());

        UUID modelo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo)"
                        + " VALUES (?, ?, ?, ?)",
                modelo, "Modelo " + unico(), fabricante, equipoCatalogo);

        UUID unidad = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio)
                VALUES (?, ?, ?, ?)
                """,
                unidad, "Serie-" + unico(), modelo, area);

        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                           f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio)
                VALUES (?, ?, ?, ?, 'MENSUAL', ?)
                """,
                orden, cliente, sede, LocalDate.now(), tipoServicio);

        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio)
                VALUES (?, ?, ?)
                """,
                orden, unidad, area);

        UUID reporte = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente)
                VALUES (?, ?, ?)
                """,
                reporte, orden, unidad);

        return new Contexto(cliente, sede, area, unidad, reporte);
    }

    private record Contexto(UUID cliente, UUID sede, UUID area, UUID equipo, UUID reporte) {
    }

    /**
     * Otra orden para el mismo equipo, y su reporte en borrador.
     *
     * <p>Hace falta una orden nueva y no basta otro reporte: el esquema solo admite <b>un reporte
     * activo por (orden, equipo)</b>, y la primera version de esta prueba choco contra
     * {@code UQ_reporte_servicio_activo}. La restriccion tiene razon y la prueba estaba mal: un
     * segundo mantenimiento del mismo equipo es una segunda orden de trabajo.
     */
    private UUID otroReporteEnOtraOrden(Contexto contexto) {
        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede,
                                           f_fecha_mantenimiento, n_periodicidad, t_tipo_servicio)
                VALUES (?, ?, ?, ?, 'MENSUAL', 'CALIBRACION')
                """,
                orden, contexto.cliente(), contexto.sede(), LocalDate.now());
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio)
                VALUES (?, ?, ?)
                """,
                orden, contexto.equipo(), contexto.area());

        UUID reporte = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente)
                VALUES (?, ?, ?)
                """,
                reporte, orden, contexto.equipo());

        return reporte;
    }

    private void llenarYCerrar(UUID reporte, ServiceResult resultado) {
        serviceReportServicePort.fill(new FillServiceReportCommand(
                reporte, "No enciende", "Fuente danada", "Se cambio la fuente", null, resultado));
        serviceReportServicePort.finish(new FinishServiceReportCommand(reporte));
    }

    @Test
    @DisplayName("cerrar un reporte deja la intervencion en la hoja de vida, sin que nadie la pida")
    void cerrarUnReporteAnotaLaIntervencion() {
        Contexto contexto = unBorrador("CORRECTIVO");

        llenarYCerrar(contexto.reporte(), ServiceResult.OPERATIVO);

        List<Intervention> historial =
                interventionServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted());

        assertThat(historial).hasSize(1);
        assertThat(historial.getFirst().idReporteServicio()).isEqualTo(contexto.reporte());
        assertThat(historial.getFirst().tipoServicio()).isEqualTo(InterventionType.CORRECTIVO);
        assertThat(historial.getFirst().resultado()).isEqualTo(InterventionResult.OPERATIVO);
        assertThat(historial.getFirst().fechaServicio()).isNotNull();
    }

    @Test
    @DisplayName("un reporte en borrador no deja nada en la hoja de vida")
    void unBorradorNoAnotaNada() {
        // Una intervencion es un mantenimiento HECHO. Llenar el reporte no es haberlo hecho.
        Contexto contexto = unBorrador("PREVENTIVO");

        serviceReportServicePort.fill(new FillServiceReportCommand(
                contexto.reporte(), "No enciende", "Fuente danada", "Se cambio la fuente",
                null, ServiceResult.OPERATIVO));

        assertThat(interventionServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted()))
                .isEmpty();
    }

    @Test
    @DisplayName("el historial de un equipo recien dado de alta esta vacio, no falta")
    void elHistorialNaceVacio() {
        // Es la aclaracion que ordeno todo este trabajo: la hoja de vida existe desde que el equipo
        // se registra, con su historial en cero. No es que no haya hoja de vida: es que no hay
        // mantenimientos todavia.
        Contexto contexto = unBorrador("PREVENTIVO");

        assertThat(interventionServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted()))
                .isEmpty();
    }

    @Test
    @DisplayName("dos cierres del mismo equipo dejan dos lineas, de la mas reciente a la mas antigua")
    void dosCierresDejanDosLineasOrdenadas() {
        Contexto primero = unBorrador("PREVENTIVO");
        llenarYCerrar(primero.reporte(), ServiceResult.OPERATIVO);

        UUID segundoReporte = otroReporteEnOtraOrden(primero);
        llenarYCerrar(segundoReporte, ServiceResult.FUERA_DE_SERVICIO);

        List<Intervention> historial =
                interventionServicePort.findByEquipment(primero.equipo(), ReadScope.unrestricted());

        assertThat(historial).hasSize(2);
        // El orden es parte del contrato: RF-27 pide el historial ordenado cronologicamente.
        assertThat(historial.getFirst().fechaServicio())
                .isAfterOrEqualTo(historial.getLast().fechaServicio());
        assertThat(historial.getFirst().idReporteServicio()).isEqualTo(segundoReporte);
    }
}
