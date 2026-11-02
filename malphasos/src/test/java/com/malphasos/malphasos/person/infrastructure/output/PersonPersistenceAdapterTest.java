package com.malphasos.malphasos.person.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
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
import org.springframework.dao.DataAccessException;
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
    @DisplayName("cambiar de correo principal en un solo guardado no choca contra la regla de uno por persona")
    void cambiarDePrincipalEnUnGuardado() {
        // Son dos UPDATE en el mismo flush —el viejo a falso, el nuevo a verdadero— y Hibernate no
        // garantiza su orden. Con un índice único parcial esto fallaría según el orden; la restricción
        // de exclusión de V40 es diferida y se comprueba al confirmar, con las dos filas en su sitio.
        Person persona = personWithContacts();
        persona.getEmailPersonList().getFirst().setPrincipal(true);
        EmailPerson segundo = EmailPerson.builder()
                .idCorreoPersona(UUID.randomUUID())
                .correoPersona("ada@otro.local")
                .estadoActivo(true)
                .build();
        // Delante en la lista a propósito: así su UPDATE a verdadero sale antes que el del viejo a
        // falso, que es el orden que rompe sin el diferido. Detrás, la prueba pasaba igual sin él.
        persona.getEmailPersonList().addFirst(segundo);
        Person guardada = adapter.save(persona);

        guardada.markPrimaryEmail(segundo.getIdCorreoPersona());
        adapter.save(guardada);

        assertThat(adapter.findById(guardada.getIdentificador()).orElseThrow().primaryEmail())
                .get()
                .extracting(EmailPerson::getCorreoPersona)
                .isEqualTo("ada@otro.local");
    }

    @Test
    @DisplayName("la base rechaza dos correos principales para la misma persona")
    void dosPrincipalesNo() {
        // La regla tiene que valer también para lo que no entra por el dominio.
        Person guardada = adapter.save(personWithContacts());

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO correo_persona (k_id_correo_persona, n_correo_persona, k_identificador, "
                                + "b_estado_activo, b_principal) VALUES (?, 'a@x.local', ?, true, true), "
                                + "(?, 'b@x.local', ?, true, true)",
                        UUID.randomUUID(), guardada.getIdentificador(), UUID.randomUUID(), guardada.getIdentificador()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("EX_correo_principal_por_persona");
    }

    @Test
    @DisplayName("la base rechaza un correo principal retirado")
    void principalRetiradoNo() {
        Person guardada = adapter.save(personWithContacts());

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO correo_persona (k_id_correo_persona, n_correo_persona, k_identificador, "
                                + "b_estado_activo, b_principal) VALUES (?, 'a@x.local', ?, false, true)",
                        UUID.randomUUID(), guardada.getIdentificador()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("CHK_correo_principal_activo");
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

    // --- Cambiar el identificador (V56, 2026-11-02) -----------------------------------------

    @Test
    @DisplayName("cambiar el identificador lleva consigo los correos y los teléfonos, y deja la llave vieja sin nada")
    void cambiarIdentificador() {
        Person saved = adapter.save(personWithContacts());
        UUID viejo = saved.getIdentificador();
        UUID nuevo = UUID.randomUUID();

        Person movida = adapter.changeIdentifier(viejo, nuevo);

        assertThat(movida.getIdentificador()).isEqualTo(nuevo);
        assertThat(movida.getEmailPersonList()).singleElement().satisfies(email ->
                assertThat(email.getCorreoPersona()).isEqualTo("ada@malphasos.local"));
        assertThat(movida.getPhonePersonList()).hasSize(1);
        assertThat(adapter.findById(viejo)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM correo_persona WHERE k_identificador = ?", Integer.class, viejo)).isZero();
    }

    @Test
    @DisplayName("cambiar el identificador de una persona que no existe lo dice")
    void cambiarIdentificadorDeNadie() {
        assertThatThrownBy(() -> adapter.changeIdentifier(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    @DisplayName("toda foránea hacia persona sigue el cambio de identificador: si una no, la ficha no se mueve")
    void todasLasForaneasEnCascada() {
        // Una foránea nueva sin ON UPDATE CASCADE dejaría de poder dar acceso a quien ella nombre, y el
        // fallo aparecería en producción, con la ficha concreta. Se pregunta al catálogo y no se listan
        // a mano, para que la que entre mañana también cuente.
        List<String> sinCascada = jdbcTemplate.queryForList(
                """
                SELECT conrelid::regclass || '.' || conname FROM pg_constraint
                WHERE contype = 'f' AND confrelid = 'persona'::regclass AND confupdtype <> 'c'
                """,
                String.class);
        Integer foraneas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE contype = 'f' AND confrelid = 'persona'::regclass",
                Integer.class);

        assertThat(sinCascada).isEmpty();
        assertThat(foraneas).isEqualTo(8);
    }
}
