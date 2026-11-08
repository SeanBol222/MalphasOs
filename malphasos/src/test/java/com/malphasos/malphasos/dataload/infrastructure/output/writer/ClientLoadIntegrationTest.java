package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.ClientAcronym;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.UbicacionDelEjemplo;
import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo y el cliente del ejemplo escritos contra PostgreSQL y leídos de vuelta con los puertos de
 * {@code client}, {@code person} y {@code equipment}. Como en {@link CatalogLoadIntegrationTest}, lo que se
 * escribe entero se deshace al terminar, y los rechazos corren sin transacción propia.
 *
 * <p>Crea los países y la ciudad del ejemplo si faltan: otras clases borran {@code pais} y {@code ciudad}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(statements = {
            "DELETE FROM cliente WHERE n_razon_social LIKE 'Ocupa la sigla %'",
            "DELETE FROM persona WHERE n_primer_nombre = 'Retirada' AND n_primer_apellido = 'Carga'"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientLoadIntegrationTest {

    @Autowired private DataLoadServicePort carga;
    @Autowired private ClientPersistencePort clientes;
    @Autowired private HeadquarterPersistencePort sedes;
    @Autowired private ServiceAreaPersistencePort areas;
    @Autowired private ClientEquipmentPersistencePort equipos;
    @Autowired private PersonPersistencePort personas;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final DataLoadFile CATALOGO = EjemplosDeCarga.archivo(EjemplosDeCarga.CATALOGO);
    private static final DataLoadFile CLIENTE = EjemplosDeCarga.archivo(EjemplosDeCarga.CLIENTE);

    @BeforeEach
    void laUbicacionDelEjemplo() {
        UbicacionDelEjemplo.crear(jdbcTemplate);
    }

    private static String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    /** El ejemplo del cliente con la primera aparición de un texto cambiada: la ciudad también lleva su país. */
    private static DataLoadFile cliente(String de, String a) {
        String texto = EjemplosDeCarga.texto(EjemplosDeCarga.CLIENTE);
        String cambiado = texto.replaceFirst(Pattern.quote(de), Matcher.quoteReplacement(a));
        assertThat(cambiado).describedAs("El ejemplo tiene que traer " + de).isNotEqualTo(texto);
        return new DataLoadFile("cliente.json", cambiado.getBytes(StandardCharsets.UTF_8));
    }

    private Client elCliente() {
        return clientes.findByDocumento("900000001-1").orElseThrow();
    }

    private List<ClientEquipment> susEquipos() {
        Headquarter principal = sedes.findByClient(elCliente().getId()).getFirst();
        return equipos.findByServiceAreaIn(areas.findByHeadquarter(principal.getId()).stream().map(ServiceArea::getId).toList());
    }

    @Test
    @Transactional
    @DisplayName("el cliente entra con su sigla, sus contactos, su supervisor sin cuenta, su sede y sus areas")
    void elClienteEntero() {
        assertThat(carga.apply(List.of(CATALOGO, CLIENTE)).errores()).isEmpty();

        Client cliente = elCliente();
        assertThat(cliente.getSigla()).startsWith(ClientAcronym.base("Clínica Los Almendros S.A.S."));
        assertThat(cliente.getCorreos()).extracting(c -> c.getCorreo()).containsExactly("contacto@clinica-ejemplo.com");
        assertThat(cliente.getTelefonos()).extracting(t -> t.getTelefono()).containsExactly("6010000000");

        Person laura = personas.findAll().stream().filter(p -> p.getCedula().equals("1000000002")).findFirst().orElseThrow();
        assertThat(laura.getTipoPersona()).isEqualTo(PersonType.SUPERVISOR);
        assertThat(laura.getEmailPersonList()).singleElement().satisfies(e -> {
            assertThat(e.getCorreoPersona()).isEqualTo("laura.diaz@clinica-ejemplo.com");
            assertThat(e.isPrincipal()).describedAs("el primer correo es el de la cuenta").isTrue();
        });
        assertThat(cliente.getSupervisores()).singleElement().satisfies(s -> {
            assertThat(s.idPersona()).isEqualTo(laura.getIdentificador());
            assertThat(s.cargo()).isEqualTo("Coordinadora asistencial");
        });

        List<Headquarter> suyas = sedes.findByClient(cliente.getId());
        assertThat(suyas).singleElement().satisfies(s -> {
            assertThat(s.getNombre()).isEqualTo("Principal");
            assertThat(s.getDireccion().calle()).isEqualTo("100");
        });
        assertThat(areas.findByHeadquarter(suyas.getFirst().getId())).extracting(ServiceArea::getNombre)
                .containsExactlyInAnyOrder("Consultorio", "Enfermería");
    }

    @Test
    @Transactional
    @DisplayName("el equipo sin serie recibe la provisional, y los dos su numero de hoja de vida")
    void losEquipos() {
        assertThat(carga.apply(List.of(CATALOGO, CLIENTE)).errores()).isEmpty();

        assertThat(susEquipos()).extracting(ClientEquipment::getSerie).anySatisfy(s -> assertThat(s).isEqualTo("WA-123456"))
                .anySatisfy(s -> assertThat(s).startsWith("P:"));
        String sigla = elCliente().getSigla();
        assertThat(jdbcTemplate.queryForList("SELECT n_numero_hoja_vida FROM equipo_cliente WHERE k_id_equipo_cliente IN (?, ?)",
                String.class, susEquipos().get(0).getId(), susEquipos().get(1).getId()))
                .allSatisfy(n -> assertThat(n).startsWith("HV-" + sigla + "-"));
    }

    @Test
    @Transactional
    @DisplayName("un equipo retirado entra dado de baja")
    void retirado() {
        DataLoadFile conRetirado = cliente("\"codigoInterno\": \"TEN-01\"", "\"codigoInterno\": \"TEN-01\", \"retirado\": true");

        assertThat(carga.apply(List.of(CATALOGO, conRetirado)).errores()).isEmpty();

        assertThat(susEquipos()).filteredOn(e -> "WA-123456".equals(e.getSerie())).singleElement()
                .satisfies(e -> assertThat(e.isEstadoActivo()).isFalse());
    }

    @Test
    @Transactional
    @DisplayName("el cliente cargado otra vez no se duplica, y su equipo sin serie se rechaza: no hay con que saber si ya esta")
    void otraVez() {
        assertThat(carga.apply(List.of(CATALOGO, CLIENTE)).errores()).isEmpty();
        DataLoadReport segunda = carga.apply(List.of(CATALOGO, CLIENTE));

        assertThat(segunda.errores()).singleElement().extracting(DataLoadProblem::ruta).isEqualTo("/equipos/1");
        assertThat(clientes.findAll().stream().filter(c -> c.getDocumento().equals("900000001-1"))).hasSize(1);
        assertThat(susEquipos()).hasSize(2);
    }

    @Test
    @Transactional
    @DisplayName("con todos sus equipos con serie, el cliente se carga otra vez y la sede, las areas y los equipos se reconocen")
    void otraVezConSeries() {
        DataLoadFile conSeries = cliente("\"modelo\": \"mod-termo-digital\"", "\"modelo\": \"mod-termo-digital\", \"serie\": \"TD-77\"");
        assertThat(carga.apply(List.of(CATALOGO, conSeries)).errores()).isEmpty();

        DataLoadReport segunda = carga.apply(List.of(CATALOGO, conSeries));

        assertThat(segunda.errores()).isEmpty();
        assertThat(sedes.findByClient(elCliente().getId())).hasSize(1);
        assertThat(areas.findByHeadquarter(sedes.findByClient(elCliente().getId()).getFirst().getId())).hasSize(2);
        assertThat(susEquipos()).hasSize(2);
    }

    @Test
    @DisplayName("una sigla que ya es de otro cliente rechaza la carga, en su campo")
    void siglaOcupada() {
        String u = unico();
        String sigla = "Z" + u.substring(5);
        clientes.save(Client.create("8" + u.substring(1), IdentificationType.NIT_JURIDICO, "Ocupa la sigla " + u, null, sigla));
        DataLoadFile conSigla = cliente("\"pais\": \"COL\",", "\"pais\": \"COL\", \"sigla\": \"" + sigla + "\",");

        assertThatThrownBy(() -> carga.apply(List.of(CATALOGO, conSigla)))
                .isInstanceOfSatisfying(DataLoadRejectedException.class, e -> {
                    assertThat(e.problema().ruta()).isEqualTo("/clientes/0/sigla");
                    assertThat(e.problema().mensaje()).contains(sigla);
                });
        assertThat(clientes.findByDocumento("900000001-1")).isEmpty();
    }

    @Test
    @DisplayName("una restriccion de la base al escribir el cliente rechaza la carga entera, catalogo incluido")
    void restriccionEnElCliente() {
        String u = unico();
        // Una persona retirada con la cédula del supervisor: la carga solo reconoce a las activas, así que
        // intenta crearla, y la cédula es única en toda la tabla.
        personas.save(Person.builder().identificador(UUID.randomUUID()).cedula(u).primerNombre("Retirada")
                .primerApellido("Carga").tipoPersona(PersonType.SUPERVISOR).estadoActivo(false).build());
        DataLoadFile conEsaCedula = cliente("\"cedula\": \"1000000002\"", "\"cedula\": \"" + u + "\"");

        assertThatThrownBy(() -> carga.apply(List.of(CATALOGO, conEsaCedula)))
                .isInstanceOfSatisfying(DataLoadRejectedException.class,
                        e -> assertThat(e.problema().mensaje()).contains("La base de datos rechazó la carga"));
        assertThat(clientes.findByDocumento("900000001-1")).isEmpty();
    }
}
