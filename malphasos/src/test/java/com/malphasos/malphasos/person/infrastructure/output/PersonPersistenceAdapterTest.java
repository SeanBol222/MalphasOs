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

    @Autowired private PersonPersistenceAdapter adapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Person personWithContacts() {
        Person person = Person.builder()
                .identificador(UUID.randomUUID())
                .cedula(String.valueOf(System.nanoTime() % 10_000_000_000L))
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();

        person.addEmail(EmailPerson.builder()
                .idCorreoPersona(UUID.randomUUID())
                .correoPersona("ada@malphasos.local")
                .estadoActivo(true)
                .build());
        person.addPhone(PhonePerson.builder()
                .idTelefonoPersona(UUID.randomUUID())
                .telefonoPersona("3001234567")
                .estadoActivo(true)
                .build());

        return person;
    }

    @Test
    @DisplayName("una persona con contactos se guarda y se recupera completa")
    void saveAndRetrieve() {
        Person saved = adapter.save(personWithContacts());

        Person retrieved = adapter.findById(saved.getIdentificador()).orElseThrow();

        assertThat(retrieved.getPrimerNombre()).isEqualTo("Ada");
        assertThat(retrieved.getTipoPersona()).isEqualTo(PersonType.ENGINEER);
        assertThat(retrieved.getEmailPersonList()).singleElement().satisfies(email ->
                assertThat(email.getCorreoPersona()).isEqualTo("ada@malphasos.local"));
        assertThat(retrieved.getPhonePersonList()).singleElement().satisfies(phone ->
                assertThat(phone.getTelefonoPersona()).isEqualTo("3001234567"));
    }

    @Test
    @DisplayName("los contactos quedan enlazados a su persona por la llave foranea")
    void contactsStoreTheirForeignKey() {
        // Sin el @AfterMapping que cierra la relacion bidireccional, las filas hijas se
        // persistirian con k_identificador nulo.
        Person saved = adapter.save(personWithContacts());

        Integer orphanEmails = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM correo_persona WHERE k_identificador IS NULL", Integer.class);
        UUID emailOwnerId = jdbcTemplate.queryForObject(
                "SELECT k_identificador FROM correo_persona LIMIT 1", UUID.class);

        assertThat(orphanEmails).isZero();
        assertThat(emailOwnerId).isEqualTo(saved.getIdentificador());
    }

    @Test
    @DisplayName("el tipo de persona se guarda como texto, no como posicion del enum")
    void enumIsStoredAsText() {
        // Con EnumType.ORDINAL, reordenar el enum reinterpretaria en silencio las filas guardadas.
        adapter.save(personWithContacts());

        String type = jdbcTemplate.queryForObject(
                "SELECT t_tipo_persona FROM persona LIMIT 1", String.class);

        assertThat(type).isEqualTo("ENGINEER");
    }

    @Test
    @DisplayName("desactivar un correo lo conserva en la base en vez de borrarlo")
    void deactivatingEmailKeepsTheRow() {
        Person saved = adapter.save(personWithContacts());
        UUID emailId = saved.getEmailPersonList().getFirst().getIdCorreoPersona();

        saved.removeEmail(emailId);
        adapter.save(saved);

        var rows = jdbcTemplate.queryForList(
                "SELECT b_estado_activo FROM correo_persona WHERE k_id_correo_persona = ?", emailId);

        assertThat(rows).singleElement().satisfies(fila ->
                assertThat(fila.get("b_estado_activo")).isEqualTo(false));
    }

    @Test
    @DisplayName("actualizar los datos de una persona no borra sus contactos")
    void updateDoesNotDropContacts() {
        // orphanRemoval esta activo: si una actualizacion partiera de una persona sin sus contactos
        // cargados, Hibernate los borraria fisicamente. Este test fija el comportamiento correcto.
        Person saved = adapter.save(personWithContacts());

        Person retrieved = adapter.findById(saved.getIdentificador()).orElseThrow();
        retrieved.setPrimerNombre("Grace");
        adapter.save(retrieved);

        Person afterUpdate = adapter.findById(saved.getIdentificador()).orElseThrow();

        assertThat(afterUpdate.getPrimerNombre()).isEqualTo("Grace");
        assertThat(afterUpdate.getEmailPersonList()).hasSize(1);
        assertThat(afterUpdate.getPhonePersonList()).hasSize(1);
    }

    @Test
    @DisplayName("una persona sin segundo apellido se guarda sin problema")
    void secondSurnameIsOptional() {
        // El original marcaba esta columna como obligatoria en la entidad, en contra del esquema.
        Person withoutSecondSurname = personWithContacts();
        withoutSecondSurname.setSegundoApellido(null);

        Person saved = adapter.save(withoutSecondSurname);

        assertThat(adapter.findById(saved.getIdentificador()))
                .get()
                .satisfies(p -> assertThat(p.getSegundoApellido()).isNull());
    }

    @Test
    @DisplayName("findAll devuelve las personas guardadas")
    void listPersons() {
        adapter.save(personWithContacts());
        adapter.save(personWithContacts());

        List<Person> all = adapter.findAll();

        assertThat(all).hasSize(2);
    }
}
