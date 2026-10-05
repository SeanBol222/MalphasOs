package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.ports.input.InterventionServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.DiscardServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
            "DELETE FROM encargado",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM correo_cliente",
            "DELETE FROM telefono_cliente",
            "DELETE FROM cliente",
            "DELETE FROM persona WHERE n_primer_nombre = 'Carla' AND n_primer_apellido = 'Ruiz'"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InterventionRecordingIntegrationTest {

    @Autowired private ServiceReportServicePort serviceReportServicePort;
    @Autowired private InterventionServicePort interventionServicePort;
    @Autowired private LifeSheetServicePort lifeSheetServicePort;
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
        String iso = CodigoIsoLibre.en(jdbcTemplate);
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
        // Desde V17, tambien que se hizo: los procedimientos, y no la falla ni el diagnostico. La orden
        // de este contexto no tiene ingeniero, de modo que el responsable queda vacio.
        assertThat(historial.getFirst().descripcion()).isEqualTo("Se cambio la fuente");
        assertThat(historial.getFirst().responsable()).isNull();
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

    // ------------------------------------------------------------------------
    // La hoja de vida: el documento compilado, con el historial dentro
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("la hoja de vida de un equipo recien dado de alta esta completa, con historial en cero")
    void laHojaDeVidaNaceCompleta() {
        // Es la aclaracion que ordeno este trabajo, y por eso es la prueba que la fija: la hoja de
        // vida NO sale de los mantenimientos. Existe desde que el equipo se registra, con sus tres
        // primeras secciones llenas, y el historial empieza vacio.
        Contexto contexto = unBorrador("PREVENTIVO");

        LifeSheet hoja = lifeSheetServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted());

        assertThat(hoja.identificacion().serie()).isNotBlank();
        // El numero de la hoja (V20): lo puso el trigger al insertar el equipo, aunque entrara por SQL.
        assertThat(hoja.identificacion().numeroHojaVida()).matches("HV-[A-Z][A-Z0-9]{2,5}-0001");
        assertThat(hoja.identificacion().cliente()).isEqualTo("Hospital Central");
        assertThat(hoja.identificacion().sede()).isNotBlank();
        assertThat(hoja.identificacion().direccionSede()).isEqualTo("Calle 10 # 20 - 30-40");
        assertThat(hoja.identificacion().ciudadSede()).isNotBlank();
        assertThat(hoja.identificacion().areaServicio()).isNotBlank();

        assertThat(hoja.tecnica().tipoEquipo()).isNotBlank();
        assertThat(hoja.tecnica().definicionTecnica()).isEqualTo("Definicion");
        assertThat(hoja.tecnica().tecnologiaPredominante()).isEqualTo("Electronica");
        assertThat(hoja.tecnica().marca()).isNotBlank();
        assertThat(hoja.tecnica().modelo()).isNotBlank();

        assertThat(hoja.fabricante().nombre()).isNotBlank();

        assertThat(hoja.servicioTecnico()).isEmpty();
    }

    @Test
    @DisplayName("la hoja de vida trae la ficha del modelo, el uso del tipo y el codigo y proveedor de la unidad")
    void laFichaDelModelo() {
        // Desde V15 voltaje y amperaje salen del modelo y no del tipo. Esta prueba existe porque una
        // mutacion que devolvia la ficha vacia sobrevivio: nada miraba de donde salia.
        Contexto contexto = unBorrador("PREVENTIVO");
        jdbcTemplate.update(
                """
                UPDATE modelo SET n_clase_riesgo = 'IIA', i_voltaje = 110, d_amperaje = 2.5
                WHERE k_id_modelo = (SELECT k_id_modelo FROM equipo_cliente WHERE k_id_equipo_cliente = ?)
                """,
                contexto.equipo());
        jdbcTemplate.update(
                """
                UPDATE tipo_equipo SET t_uso = 'Pesaje de pacientes'
                WHERE k_id_tipo_equipo = (
                    SELECT e.k_id_tipo_equipo FROM equipo_cliente u
                    JOIN modelo m ON m.k_id_modelo = u.k_id_modelo
                    JOIN equipo e ON e.k_id_equipo = m.k_id_equipo
                    WHERE u.k_id_equipo_cliente = ?)
                """,
                contexto.equipo());

        jdbcTemplate.update(
                """
                UPDATE equipo_cliente SET n_codigo_interno = 'BAL-07', n_proveedor = 'Distribuidora Medica'
                WHERE k_id_equipo_cliente = ?
                """,
                contexto.equipo());

        LifeSheet hoja = lifeSheetServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted());

        assertThat(hoja.identificacion().codigoInterno()).isEqualTo("BAL-07");
        assertThat(hoja.identificacion().proveedor()).isEqualTo("Distribuidora Medica");
        assertThat(hoja.tecnica().fichaTecnica().riesgo()).isEqualTo(RiskClass.IIA);
        assertThat(hoja.tecnica().fichaTecnica().voltaje()).isEqualTo(110);
        assertThat(hoja.tecnica().fichaTecnica().amperaje()).isEqualByComparingTo("2.50");
        assertThat(hoja.tecnica().uso()).isEqualTo("Pesaje de pacientes");
    }

    @Test
    @DisplayName("la hoja de vida trae el responsable de la sede y los contactos vigentes del cliente")
    void elResponsableYLosContactos() {
        // De extremo a extremo y no con dobles: cruza tres modulos —equipment pide, client decide quien
        // responde, person pone el nombre— y lo que hay que ver es que el cableado llega.
        Contexto contexto = unBorrador("PREVENTIVO");
        UUID persona = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido,
                                     t_tipo_persona)
                VALUES (?, ?, 'Carla', 'Ruiz', 'MANAGER')
                """,
                persona, unico());
        // Encargada de la SEDE, no del area: el area no tiene, asi que responde la sede.
        jdbcTemplate.update(
                "INSERT INTO encargado (k_identificador, t_tipo_encargado, k_id_sede) VALUES (?, 'HEADQUARTER', ?)",
                persona, contexto.sede());
        jdbcTemplate.update(
                "INSERT INTO telefono_cliente (k_id_telefono_cliente, n_telefono_cliente, k_id_cliente) VALUES (?, '3001112233', ?)",
                UUID.randomUUID(), contexto.cliente());
        jdbcTemplate.update(
                """
                INSERT INTO telefono_cliente (k_id_telefono_cliente, n_telefono_cliente, k_id_cliente,
                                              b_estado_activo)
                VALUES (?, '6010000000', ?, false)
                """,
                UUID.randomUUID(), contexto.cliente());
        jdbcTemplate.update(
                "INSERT INTO correo_cliente (k_id_correo_cliente, n_correo_cliente, k_id_cliente) VALUES (?, 'compras@hospital.co', ?)",
                UUID.randomUUID(), contexto.cliente());

        LifeSheet hoja = lifeSheetServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted());

        assertThat(hoja.identificacion().responsables()).containsExactly("Carla Ruiz");
        // El telefono retirado no sale: un numero que ya no contesta, impreso en la hoja, es peor que
        // ninguno.
        assertThat(hoja.identificacion().telefonosCliente()).containsExactly("3001112233");
        assertThat(hoja.identificacion().correosCliente()).containsExactly("compras@hospital.co");
    }

    @Test
    @DisplayName("al cerrar un reporte, la cuarta seccion de la hoja de vida deja de estar vacia")
    void laCuartaSeccionSeLlenaAlCerrar() {
        Contexto contexto = unBorrador("CALIBRACION");

        llenarYCerrar(contexto.reporte(), ServiceResult.OPERATIVO);

        LifeSheet hoja = lifeSheetServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted());

        assertThat(hoja.servicioTecnico()).hasSize(1);
        assertThat(hoja.servicioTecnico().getFirst().tipoServicio()).isEqualTo(InterventionType.CALIBRACION);
        // Y las otras tres secciones siguen ahi: cerrar un reporte no cambia que es el equipo.
        assertThat(hoja.tecnica().tipoEquipo()).isNotBlank();
    }

    @Test
    @DisplayName("la hoja de vida de un equipo ajeno no existe para quien pregunta")
    void laHojaDeVidaDeUnEquipoAjeno() {
        Contexto contexto = unBorrador("PREVENTIVO");
        ReadScope deOtroCliente = ReadScope.ofClients(java.util.Set.of(UUID.randomUUID()));

        assertThatThrownBy(() -> lifeSheetServicePort.findByEquipment(contexto.equipo(), deOtroCliente))
                .isInstanceOf(ClientEquipmentNotFoundException.class);
    }

    // ------------------------------------------------------------------------
    // Corregir un reporte: el sustituto reemplaza al anterior
    // ------------------------------------------------------------------------

    /** Un reporte nuevo en borrador sobre la misma orden y el mismo equipo que el del contexto. */
    private UUID otroBorradorDeLaMismaOrden(Contexto contexto) {
        UUID orden = jdbcTemplate.queryForObject(
                "SELECT k_id_orden_trabajo FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                UUID.class, contexto.reporte());
        UUID reporte = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente)
                VALUES (?, ?, ?)
                """,
                reporte, orden, contexto.equipo());

        return reporte;
    }

    @Test
    @DisplayName("corregir un reporte deja UNA linea en la hoja de vida, y la vieja apuntando a la nueva")
    void corregirUnReporteNoDuplicaElMantenimiento() {
        // El flujo que la pantalla de un reporte cerrado indica: retirarlo y abrir otro. Antes de V14
        // dejaba dos lineas para un solo mantenimiento.
        Contexto contexto = unBorrador("PREVENTIVO");
        llenarYCerrar(contexto.reporte(), ServiceResult.FUERA_DE_SERVICIO);

        serviceReportServicePort.discard(new DiscardServiceReportCommand(contexto.reporte()));
        UUID sustituto = otroBorradorDeLaMismaOrden(contexto);
        llenarYCerrar(sustituto, ServiceResult.OPERATIVO);

        List<Intervention> historial =
                interventionServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted());
        assertThat(historial).hasSize(1);
        assertThat(historial.getFirst().idReporteServicio()).isEqualTo(sustituto);
        assertThat(historial.getFirst().resultado()).isEqualTo(InterventionResult.OPERATIVO);

        // La vieja no se borra: es el rastro de que hubo una correccion.
        Map<String, Object> vieja = jdbcTemplate.queryForMap(
                "SELECT b_estado_activo, k_id_reemplazada_por FROM intervencion WHERE k_id_reporte_servicio = ?",
                contexto.reporte());
        assertThat(vieja).containsEntry("b_estado_activo", false);
        assertThat(vieja.get("k_id_reemplazada_por")).isEqualTo(historial.getFirst().id());
    }

    @Test
    @DisplayName("retirar un reporte cerrado SIN abrir sustituto deja su linea en la hoja de vida")
    void retirarSinSustitutoNoBorraElMantenimiento() {
        // La otra mitad de la decision: un mantenimiento hecho no desaparece porque su reporte se
        // retire. Solo deja de contar cuando otro cierre lo sustituye.
        Contexto contexto = unBorrador("CORRECTIVO");
        llenarYCerrar(contexto.reporte(), ServiceResult.OPERATIVO);

        serviceReportServicePort.discard(new DiscardServiceReportCommand(contexto.reporte()));

        assertThat(interventionServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted()))
                .hasSize(1);
    }

    @Test
    @DisplayName("un mantenimiento en OTRA orden del mismo equipo no reemplaza a nadie")
    void otraOrdenNoEsUnaCorreccion() {
        Contexto contexto = unBorrador("PREVENTIVO");
        llenarYCerrar(contexto.reporte(), ServiceResult.OPERATIVO);

        llenarYCerrar(otroReporteEnOtraOrden(contexto), ServiceResult.OPERATIVO);

        assertThat(interventionServicePort.findByEquipment(contexto.equipo(), ReadScope.unrestricted()))
                .hasSize(2);
    }
}
