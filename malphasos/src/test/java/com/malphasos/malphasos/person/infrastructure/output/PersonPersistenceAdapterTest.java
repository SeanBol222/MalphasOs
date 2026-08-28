package com.malphasos.malphasos.person.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.person.domain.person.PhonePerson;
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
 * Ejercita el adaptador de persistencia contra un PostgreSQL real: el mapeo entre dominio y
 * entidades, la llave foránea de los contactos y el enum guardado como texto solo se pueden
 * comprobar de verdad en el motor.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM correo_persona",
            "DELETE FROM telefono_persona",
            "DELETE FROM persona"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PersonPersistenceAdapterTest {

    @Autowired private PersonPersistenceAdapter adaptador;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Person personaConContactos() {
        Person persona = Person.builder()
                .identificador(UUID.randomUUID())
                .cedula(String.valueOf(System.nanoTime() % 10_000_000_000L))
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();

        persona.addEmail(EmailPerson.builder()
                .idCorreoPersona(UUID.randomUUID())
                .correoPersona("ada@malphasos.local")
                .estadoActivo(true)
                .build());
        persona.addPhone(PhonePerson.builder()
                .idTelefonoPersona(UUID.randomUUID())
                .telefonoPersona("3001234567")
                .estadoActivo(true)
                .build());

        return persona;
    }

    @Test
    @DisplayName("una persona con contactos se guarda y se recupera completa")
    void guardarYRecuperar() {
        Person guardada = adaptador.save(personaConContactos());

        Person recuperada = adaptador.findById(guardada.getIdentificador()).orElseThrow();

        assertThat(recuperada.getPrimerNombre()).isEqualTo("Ada");
        assertThat(recuperada.getTipoPersona()).isEqualTo(PersonType.ENGINEER);
        assertThat(recuperada.getEmailPersonList()).singleElement().satisfies(correo ->
                assertThat(correo.getCorreoPersona()).isEqualTo("ada@malphasos.local"));
        assertThat(recuperada.getPhonePersonList()).singleElement().satisfies(telefono ->
                assertThat(telefono.getTelefonoPersona()).isEqualTo("3001234567"));
    }

    @Test
    @DisplayName("los contactos quedan enlazados a su persona por la llave foranea")
    void losContactosGuardanSuLlaveForanea() {
        // Sin el @AfterMapping que cierra la relacion bidireccional, las filas hijas se
        // persistirian con k_identificador nulo.
        Person guardada = adaptador.save(personaConContactos());

        Integer correosHuerfanos = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM correo_persona WHERE k_identificador IS NULL", Integer.class);
        UUID personaDelCorreo = jdbcTemplate.queryForObject(
                "SELECT k_identificador FROM correo_persona LIMIT 1", UUID.class);

        assertThat(correosHuerfanos).isZero();
        assertThat(personaDelCorreo).isEqualTo(guardada.getIdentificador());
    }

    @Test
    @DisplayName("el tipo de persona se guarda como texto, no como posicion del enum")
    void elEnumSeGuardaComoTexto() {
        // Con EnumType.ORDINAL, reordenar el enum reinterpretaria en silencio las filas guardadas.
        adaptador.save(personaConContactos());

        String tipo = jdbcTemplate.queryForObject(
                "SELECT t_tipo_persona FROM persona LIMIT 1", String.class);

        assertThat(tipo).isEqualTo("ENGINEER");
    }

    @Test
    @DisplayName("desactivar un correo lo conserva en la base en vez de borrarlo")
    void desactivarUnCorreoNoBorraLaFila() {
        Person guardada = adaptador.save(personaConContactos());
        UUID idCorreo = guardada.getEmailPersonList().getFirst().getIdCorreoPersona();

        guardada.removeEmail(idCorreo);
        adaptador.save(guardada);

        var filas = jdbcTemplate.queryForList(
                "SELECT b_estado_activo FROM correo_persona WHERE k_id_correo_persona = ?", idCorreo);

        assertThat(filas).singleElement().satisfies(fila ->
                assertThat(fila.get("b_estado_activo")).isEqualTo(false));
    }

    @Test
    @DisplayName("actualizar los datos de una persona no borra sus contactos")
    void actualizarNoArrastraLosContactos() {
        // orphanRemoval esta activo: si una actualizacion partiera de una persona sin sus contactos
        // cargados, Hibernate los borraria fisicamente. Este test fija el comportamiento correcto.
        Person guardada = adaptador.save(personaConContactos());

        Person recuperada = adaptador.findById(guardada.getIdentificador()).orElseThrow();
        recuperada.setPrimerNombre("Grace");
        adaptador.save(recuperada);

        Person trasActualizar = adaptador.findById(guardada.getIdentificador()).orElseThrow();

        assertThat(trasActualizar.getPrimerNombre()).isEqualTo("Grace");
        assertThat(trasActualizar.getEmailPersonList()).hasSize(1);
        assertThat(trasActualizar.getPhonePersonList()).hasSize(1);
    }

    @Test
    @DisplayName("una persona sin segundo apellido se guarda sin problema")
    void elSegundoApellidoEsOpcional() {
        // El original marcaba esta columna como obligatoria en la entidad, en contra del esquema.
        Person sinSegundoApellido = personaConContactos();
        sinSegundoApellido.setSegundoApellido(null);

        Person guardada = adaptador.save(sinSegundoApellido);

        assertThat(adaptador.findById(guardada.getIdentificador()))
                .get()
                .satisfies(p -> assertThat(p.getSegundoApellido()).isNull());
    }

    @Test
    @DisplayName("findAll devuelve las personas guardadas")
    void listarPersonas() {
        adaptador.save(personaConContactos());
        adaptador.save(personaConContactos());

        List<Person> todas = adaptador.findAll();

        assertThat(todas).hasSize(2);
    }
}
