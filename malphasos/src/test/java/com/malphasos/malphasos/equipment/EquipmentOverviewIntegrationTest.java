package com.malphasos.malphasos.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentOverviewServicePort;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * El listado de equipos del rediseño (2026-10-11), contra PostgreSQL real: la consulta es SQL escrito a
 * mano, con un {@code LATERAL}, un {@code LIKE} con escape y filtros que se componen, y nada de eso se
 * puede probar con un doble.
 *
 * <p>Casi todas las pruebas consultan con un alcance <b>restringido a sus propios clientes</b>. No es
 * solo aislamiento: así cada una ejerce también el paso de clientes a áreas, que es la mitad del
 * servicio.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM intervencion",
            "DELETE FROM dato_verificacion",
            "DELETE FROM paso_reporte",
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo_equipo",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM fabricante",
            "DELETE FROM marca",
            "DELETE FROM protocolo_tipo_equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM encargado",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM correo_cliente",
            "DELETE FROM telefono_cliente",
            "DELETE FROM cliente"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EquipmentOverviewIntegrationTest {

    @Autowired private EquipmentOverviewServicePort servicio;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private record Lugar(UUID cliente, UUID sede, UUID area, String nombreCliente, String nombreSede, String nombreArea) {
    }

    private record Catalogo(UUID tipo, UUID modelo, String nombreTipo, String marca, String nombreModelo) {
    }

    private Lugar unLugar(String razonSocial) {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', ?)
                """,
                cliente, unico(), razonSocial);

        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                pais, CodigoIsoLibre.en(jdbcTemplate), "Pais " + unico());
        jdbcTemplate.update(
                "INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Ciudad " + unico(), pais);

        UUID sede = UUID.randomUUID();
        String nombreSede = "Sede " + unico();
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30', ?, ?)
                """,
                sede, nombreSede, cliente, ciudad);

        UUID area = UUID.randomUUID();
        String nombreArea = "Area " + unico();
        jdbcTemplate.update(
                "INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, nombreArea, sede);

        return new Lugar(cliente, sede, area, razonSocial, nombreSede, nombreArea);
    }

    private Catalogo unModelo(String nombreTipo, String nombreModelo) {
        UUID tipo = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                tipo, nombreTipo + " " + unico());

        UUID marca = UUID.randomUUID();
        String nombreMarca = "Marca " + unico();
        jdbcTemplate.update("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", marca, nombreMarca);

        UUID equipo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)", equipo, tipo, marca);

        UUID fabricante = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                fabricante, "Fabricante " + unico());

        UUID modelo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                modelo, nombreModelo, fabricante, equipo);

        String tipoGuardado = jdbcTemplate.queryForObject(
                "SELECT n_nombre_tipo_equipo FROM tipo_equipo WHERE k_id_tipo_equipo = ?", String.class, tipo);

        return new Catalogo(tipo, modelo, tipoGuardado, nombreMarca, nombreModelo);
    }

    private UUID unEquipo(Lugar lugar, Catalogo catalogo, String serie) {
        UUID unidad = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                unidad, serie, catalogo.modelo(), lugar.area());

        return unidad;
    }

    /** Un servicio cerrado: orden, reporte e intervención, que es lo que la hoja de vida cuenta. */
    private void unServicio(Lugar lugar, UUID unidad, LocalDateTime fecha, InterventionResult resultado, boolean vigente) {
        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede, f_fecha_mantenimiento,
                                           n_periodicidad, t_tipo_servicio)
                VALUES (?, ?, ?, ?, 'MENSUAL', 'PREVENTIVO')
                """,
                orden, lugar.cliente(), lugar.sede(), LocalDate.now());
        jdbcTemplate.update(
                "INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio) VALUES (?, ?, ?)",
                orden, unidad, lugar.area());

        UUID reporte = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente) VALUES (?, ?, ?)",
                reporte, orden, unidad);
        jdbcTemplate.update(
                """
                INSERT INTO intervencion (k_id_intervencion, k_id_equipo_cliente, k_id_reporte_servicio,
                                          f_fecha_servicio, t_tipo_servicio, t_resultado, b_estado_activo)
                VALUES (?, ?, ?, ?, 'PREVENTIVO', ?, ?)
                """,
                UUID.randomUUID(), unidad, reporte, fecha, resultado.name(), vigente);
    }

    private static EquipmentOverviewQuery consulta() {
        return new EquipmentOverviewQuery(null, null, null, null, false, 0, 25);
    }

    private EquipmentOverview listar(EquipmentOverviewQuery consulta, Lugar... lugares) {
        return servicio.list(consulta, ReadScope.ofClients(List.of(lugares).stream().map(Lugar::cliente).toList()));
    }

    private static List<String> series(EquipmentOverview pagina) {
        return pagina.filas().stream().map(EquipmentOverview.Row::serie).toList();
    }

    // ------------------------------------------------------------------------

    @Test
    @DisplayName("cada fila llega compilada: hoja de vida, catalogo, ubicacion y el ultimo servicio vigente")
    void filaCompilada() {
        Lugar lugar = unLugar("Clinica del Norte");
        Catalogo catalogo = unModelo("Balanza", "PCB 3500-2");
        UUID unidad = unEquipo(lugar, catalogo, "WB1240337");
        unServicio(lugar, unidad, LocalDateTime.of(2026, 1, 10, 9, 0), InterventionResult.FUERA_DE_SERVICIO, true);
        unServicio(lugar, unidad, LocalDateTime.of(2026, 3, 5, 15, 30), InterventionResult.OPERATIVO, true);
        // Mas reciente pero retirada: una intervencion reemplazada no es el ultimo servicio.
        unServicio(lugar, unidad, LocalDateTime.of(2026, 5, 1, 8, 0), InterventionResult.FUERA_DE_SERVICIO, false);

        EquipmentOverview pagina = listar(consulta(), lugar);

        assertThat(pagina.total()).isEqualTo(1);
        EquipmentOverview.Row fila = pagina.filas().getFirst();
        assertThat(fila.id()).isEqualTo(unidad);
        assertThat(fila.numeroHojaVida()).matches("HV-[A-Z][A-Z0-9]{2,5}-0001");
        assertThat(fila.tipoEquipo()).isEqualTo(catalogo.nombreTipo());
        assertThat(fila.idTipoEquipo()).isEqualTo(catalogo.tipo());
        assertThat(fila.marca()).isEqualTo(catalogo.marca());
        assertThat(fila.modelo()).isEqualTo("PCB 3500-2");
        assertThat(fila.ultimoServicio()).isEqualTo(LocalDateTime.of(2026, 3, 5, 15, 30));
        assertThat(fila.resultado()).isEqualTo(InterventionResult.OPERATIVO);
        assertThat(fila.ubicacion().cliente()).isEqualTo("Clinica del Norte");
        assertThat(fila.ubicacion().idCliente()).isEqualTo(lugar.cliente());
        assertThat(fila.ubicacion().sede()).isEqualTo(lugar.nombreSede());
        assertThat(fila.ubicacion().area()).isEqualTo(lugar.nombreArea());
    }

    @Test
    @DisplayName("un equipo sin servicios sale sin fecha ni resultado, y filtrar por resultado lo deja fuera")
    void sinServicios() {
        Lugar lugar = unLugar("Hospital San Rafael");
        Catalogo catalogo = unModelo("Monitor", "uMEC10");
        unEquipo(lugar, catalogo, "SIN-SERVICIO");
        UUID fuera = unEquipo(lugar, catalogo, "FUERA");
        unServicio(lugar, fuera, LocalDateTime.of(2026, 9, 12, 10, 0), InterventionResult.FUERA_DE_SERVICIO, true);

        EquipmentOverview todos = listar(consulta(), lugar);
        EquipmentOverview.Row sinServicio = todos.filas().stream()
                .filter(f -> f.serie().equals("SIN-SERVICIO")).findFirst().orElseThrow();
        assertThat(sinServicio.ultimoServicio()).isNull();
        assertThat(sinServicio.resultado()).isNull();

        EquipmentOverview fueraDeServicio = listar(
                new EquipmentOverviewQuery(null, null, null, InterventionResult.FUERA_DE_SERVICIO, false, 0, 25), lugar);
        assertThat(series(fueraDeServicio)).containsExactly("FUERA");
        assertThat(fueraDeServicio.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("el texto busca en serie, hoja de vida, modelo, tipo y marca, sin tildes ni mayusculas")
    void buscarTexto() {
        Lugar lugar = unLugar("Laboratorio Andino");
        Catalogo centrifuga = unModelo("Centrífuga", "EBA 200");
        Catalogo termo = unModelo("Termohigrómetro", "Extech 445702");
        unEquipo(lugar, centrifuga, "0004712-01");
        unEquipo(lugar, termo, "Q298113");

        assertThat(series(listar(new EquipmentOverviewQuery("CENTRIFUGA", null, null, null, false, 0, 25), lugar)))
                .containsExactly("0004712-01");
        // Con tilde en lo escrito tambien: se quita en los dos lados.
        assertThat(series(listar(new EquipmentOverviewQuery("Termohigrómetro", null, null, null, false, 0, 25), lugar)))
                .containsExactly("Q298113");
        assertThat(series(listar(new EquipmentOverviewQuery("extech", null, null, null, false, 0, 25), lugar)))
                .containsExactly("Q298113");
        assertThat(series(listar(new EquipmentOverviewQuery("q2981", null, null, null, false, 0, 25), lugar)))
                .containsExactly("Q298113");
        assertThat(series(listar(new EquipmentOverviewQuery(termo.marca(), null, null, null, false, 0, 25), lugar)))
                .containsExactly("Q298113");
        // Lo que se escribe es texto y no patron: un «%» no casa con todo.
        assertThat(listar(new EquipmentOverviewQuery("%", null, null, null, false, 0, 25), lugar).filas()).isEmpty();
        assertThat(listar(new EquipmentOverviewQuery("_", null, null, null, false, 0, 25), lugar).filas()).isEmpty();
    }

    @Test
    @DisplayName("el numero de hoja de vida se busca tal cual se lee")
    void buscarHojaDeVida() {
        Lugar lugar = unLugar("IPS Salud Total");
        Catalogo catalogo = unModelo("Pulsioximetro", "9590");
        UUID unidad = unEquipo(lugar, catalogo, "502387114");
        String numero = jdbcTemplate.queryForObject(
                "SELECT n_numero_hoja_vida FROM equipo_cliente WHERE k_id_equipo_cliente = ?", String.class, unidad);

        assertThat(series(listar(new EquipmentOverviewQuery(numero.toLowerCase(), null, null, null, false, 0, 25), lugar)))
                .containsExactly("502387114");
    }

    @Test
    @DisplayName("el tipo filtra, y los dados de baja salen solo cuando se piden")
    void tipoYBaja() {
        Lugar lugar = unLugar("Fundacion Medica Sabana");
        Catalogo autoclave = unModelo("Autoclave", "2540 EK");
        Catalogo ecg = unModelo("Electrocardiografo", "ECG300G");
        unEquipo(lugar, autoclave, "AUTO-1");
        unEquipo(lugar, ecg, "ECG-1");
        UUID baja = unEquipo(lugar, ecg, "ECG-BAJA");
        jdbcTemplate.update("UPDATE equipo_cliente SET b_estado_activo = false WHERE k_id_equipo_cliente = ?", baja);

        assertThat(series(listar(new EquipmentOverviewQuery(null, null, ecg.tipo(), null, false, 0, 25), lugar)))
                .containsExactly("ECG-1");
        assertThat(series(listar(new EquipmentOverviewQuery(null, null, null, null, true, 0, 25), lugar)))
                .containsExactly("ECG-BAJA");
    }

    @Test
    @DisplayName("por identificador trae esos equipos esten como esten, y el alcance sigue mandando")
    void porIdentificador() {
        // Es lo que pide la ficha de una orden: sus equipos, incluido el que se dio de baja despues
        // de entrar en ella. Filtrar por baja ahi lo haria desaparecer de la orden.
        Lugar propio = unLugar("Clinica de la orden");
        Lugar ajeno = unLugar("Otra clinica");
        Catalogo catalogo = unModelo("Balanza", "Kern");
        UUID enServicio = unEquipo(propio, catalogo, "EN-SERVICIO");
        UUID deBaja = unEquipo(propio, catalogo, "DE-BAJA");
        unEquipo(propio, catalogo, "NO-PEDIDO");
        UUID deOtro = unEquipo(ajeno, catalogo, "DE-OTRO");
        jdbcTemplate.update("UPDATE equipo_cliente SET b_estado_activo = false WHERE k_id_equipo_cliente = ?", deBaja);

        EquipmentOverview pagina = listar(
                new EquipmentOverviewQuery(null, null, null, null, false, Set.of(enServicio, deBaja, deOtro), 0, 25),
                propio);

        assertThat(series(pagina)).containsExactlyInAnyOrder("EN-SERVICIO", "DE-BAJA");
        assertThat(pagina.filas()).filteredOn(f -> f.serie().equals("DE-BAJA"))
                .singleElement().satisfies(f -> assertThat(f.estadoActivo()).isFalse());
    }

    @Test
    @DisplayName("el alcance limita a los clientes visibles, y filtrar por uno ajeno da una lista vacia")
    void alcance() {
        Lugar propio = unLugar("Cliente propio");
        Lugar ajeno = unLugar("Cliente ajeno");
        Catalogo catalogo = unModelo("Balanza", "Kern");
        unEquipo(propio, catalogo, "PROPIO");
        unEquipo(ajeno, catalogo, "AJENO");

        assertThat(series(listar(consulta(), propio))).containsExactly("PROPIO");

        EquipmentOverview porElAjeno = listar(new EquipmentOverviewQuery(null, ajeno.cliente(), null, null, false, 0, 25), propio);
        assertThat(porElAjeno.filas()).isEmpty();
        assertThat(porElAjeno.total()).isZero();

        // Sin restriccion, el filtro por cliente es lo que acota.
        EquipmentOverview sinRestriccion = servicio.list(
                new EquipmentOverviewQuery(null, ajeno.cliente(), null, null, false, 0, 25), ReadScope.unrestricted());
        assertThat(series(sinRestriccion)).containsExactly("AJENO");

        // Un alcance sin clientes no ve nada, ni pregunta con un «IN ()».
        assertThat(servicio.list(consulta(), ReadScope.ofClients(Set.of())).filas()).isEmpty();
    }

    @Test
    @DisplayName("la pagina trae su parte, el total cuenta todos, y van en el orden de la hoja de vida")
    void paginas() {
        Lugar lugar = unLugar("Hospital Comunal");
        Catalogo catalogo = unModelo("Tensiometro", "HEM-7120");
        unEquipo(lugar, catalogo, "C");
        unEquipo(lugar, catalogo, "A");
        unEquipo(lugar, catalogo, "B");

        EquipmentOverview primera = listar(new EquipmentOverviewQuery(null, null, null, null, false, 0, 2), lugar);
        EquipmentOverview segunda = listar(new EquipmentOverviewQuery(null, null, null, null, false, 1, 2), lugar);

        // El numero de hoja de vida sigue el orden de alta, y es el orden del listado.
        assertThat(series(primera)).containsExactly("C", "A");
        assertThat(series(segunda)).containsExactly("B");
        assertThat(primera.total()).isEqualTo(3);
        assertThat(segunda.total()).isEqualTo(3);
        assertThat(segunda.pagina()).isEqualTo(1);
    }

    @Test
    @DisplayName("un tamano fuera de rango se acota, no se rechaza")
    void tamanoAcotado() {
        assertThat(new EquipmentOverviewQuery(" ", null, null, null, false, -3, 0))
                .satisfies(c -> {
                    assertThat(c.texto()).isNull();
                    assertThat(c.pagina()).isZero();
                    assertThat(c.tamano()).isEqualTo(1);
                });
        assertThat(new EquipmentOverviewQuery(null, null, null, null, false, 0, 5000).tamano())
                .isEqualTo(EquipmentOverviewQuery.TAMANO_MAXIMO);
    }
}
