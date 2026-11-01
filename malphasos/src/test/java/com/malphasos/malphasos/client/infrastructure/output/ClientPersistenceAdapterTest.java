package com.malphasos.malphasos.client.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.ClientSupervisor;
import com.malphasos.malphasos.client.domain.client.EmailClient;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Ejercita el adaptador contra un PostgreSQL real. Lo que importa aquí es el viaje de ida y vuelta
 * del agregado, y sobre todo la sincronización de los supervisores, que es la única parte
 * del mapeo con reglas propias.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM supervisor_cliente",
            "DELETE FROM correo_cliente",
            "DELETE FROM telefono_cliente",
            "DELETE FROM cliente",
            "DELETE FROM persona"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientPersistenceAdapterTest {

    @Autowired private ClientPersistenceAdapter adapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.valueOf(System.nanoTime() % 10_000_000_000L);
    }

    private Client unCliente() {
        // Con sigla explicita y distinta: el create de cuatro argumentos no desempata —eso es del
        // servicio—, y «Hospital» mas diez digitos da casi siempre la misma sigla base.
        return Client.create(unico(), IdentificationType.NIT_JURIDICO, "Hospital " + unico(), null,
                "H" + String.format("%05d", Math.floorMod(System.nanoTime(), 100_000L)));
    }

    private UUID unaPersona() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido,
                                     t_tipo_persona)
                VALUES (?, ?, 'Ada', 'Lovelace', 'SUPERVISOR')
                """,
                id, unico());

        return id;
    }

    @Test
    @DisplayName("un cliente guardado se recupera con todos sus campos")
    void idaYVuelta() {
        Client guardado = adapter.save(unCliente());

        Client recuperado = adapter.findById(guardado.getId()).orElseThrow();

        assertThat(recuperado.getDocumento()).isEqualTo(guardado.getDocumento());
        assertThat(recuperado.getTipoIdentificacion()).isEqualTo(IdentificationType.NIT_JURIDICO);
        assertThat(recuperado.isEstadoActivo()).isTrue();
        assertThat(recuperado.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("el tipo de identificacion viaja con el valor que admite la restriccion")
    void tipoEnElEsquema() {
        Client guardado = adapter.save(
                Client.create(unico(), IdentificationType.NIT_JURIDICO, "Hospital", null));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT n_tipo_identificacion FROM cliente WHERE k_id_cliente = ?",
                        String.class, guardado.getId()))
                .isEqualTo("NIT_juridico");
    }

    @Test
    @DisplayName("los correos se guardan y se recuperan, sin duplicarse al guardar dos veces")
    void correosSinDuplicar() {
        Client cliente = unCliente();
        cliente.addEmail("contacto@hospital.com");
        Client guardado = adapter.save(cliente);

        adapter.save(guardado);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM correo_cliente WHERE k_id_cliente = ?",
                        Integer.class, guardado.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("retirar un correo conserva la fila con el estado en falso")
    void retirarCorreoConservaLaFila() {
        Client cliente = unCliente();
        UUID idCorreo = cliente.addEmail("contacto@hospital.com").getId();
        Client guardado = adapter.save(cliente);

        Client recuperado = adapter.findById(guardado.getId()).orElseThrow();
        recuperado.removeEmail(idCorreo);
        adapter.save(recuperado);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT b_estado_activo FROM correo_cliente WHERE k_id_correo_cliente = ?",
                        Boolean.class, idCorreo))
                .isFalse();
        assertThat(adapter.findById(guardado.getId()).orElseThrow().getCorreos())
                .singleElement()
                .extracting(EmailClient::isEstadoActivo).isEqualTo(false);
    }

    @Test
    @DisplayName("nombrar un supervisor crea su fila en la tabla de union")
    void nombrarSupervisor() {
        UUID persona = unaPersona();
        Client cliente = unCliente();
        cliente.appointSupervisor(persona, null);

        Client guardado = adapter.save(cliente);

        assertThat(adapter.findById(guardado.getId()).orElseThrow().getSupervisores())
                .extracting(ClientSupervisor::idPersona).containsExactly(persona);
    }

    @Test
    @DisplayName("el cargo del supervisor se guarda en su fila, cambia y vuelve con el cliente")
    void cargoDelSupervisor() {
        UUID persona = unaPersona();
        Client cliente = unCliente();
        cliente.appointSupervisor(persona, "Médico propietario");
        Client guardado = adapter.save(cliente);

        assertThat(adapter.findById(guardado.getId()).orElseThrow().getSupervisores())
                .containsExactly(new ClientSupervisor(persona, "Médico propietario"));

        Client recuperado = adapter.findById(guardado.getId()).orElseThrow();
        recuperado.changeSupervisorTitle(persona, "Ingeniero biomédico");
        adapter.save(recuperado);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT n_cargo FROM supervisor_cliente WHERE k_identificador = ?", String.class, persona))
                .isEqualTo("Ingeniero biomédico");
    }

    @Test
    @DisplayName("retirar un supervisor deja su fila inactiva, no la borra")
    void retirarSupervisorConservaLaFila() {
        UUID persona = unaPersona();
        Client cliente = unCliente();
        cliente.appointSupervisor(persona, null);
        Client guardado = adapter.save(cliente);

        Client recuperado = adapter.findById(guardado.getId()).orElseThrow();
        recuperado.removeSupervisor(persona);
        adapter.save(recuperado);

        // La fila sigue ahi, como historial, pero el agregado ya no lo cuenta entre los suyos.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT b_estado_activo FROM supervisor_cliente WHERE k_identificador = ?",
                        Boolean.class, persona))
                .isFalse();
        assertThat(adapter.findById(guardado.getId()).orElseThrow().getSupervisores()).isEmpty();
    }

    @Test
    @DisplayName("volver a nombrar a quien fue retirado reactiva su fila en vez de crear otra")
    void reactivarSupervisor() {
        UUID persona = unaPersona();
        Client cliente = unCliente();
        cliente.appointSupervisor(persona, null);
        Client guardado = adapter.save(cliente);

        Client retirado = adapter.findById(guardado.getId()).orElseThrow();
        retirado.removeSupervisor(persona);
        adapter.save(retirado);

        Client renombrado = adapter.findById(guardado.getId()).orElseThrow();
        renombrado.appointSupervisor(persona, null);
        adapter.save(renombrado);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM supervisor_cliente WHERE k_identificador = ?",
                        Integer.class, persona))
                .isEqualTo(1);
        assertThat(adapter.findById(guardado.getId()).orElseThrow().getSupervisores())
                .extracting(ClientSupervisor::idPersona).containsExactly(persona);
    }

    @Test
    @DisplayName("una persona puede supervisar a dos clientes a la vez")
    void supervisorDeVariosClientes() {
        UUID persona = unaPersona();

        Client uno = unCliente();
        uno.appointSupervisor(persona, null);
        Client otro = unCliente();
        otro.appointSupervisor(persona, null);

        UUID idUno = adapter.save(uno).getId();
        UUID idOtro = adapter.save(otro).getId();

        assertThat(adapter.findById(idUno).orElseThrow().getSupervisores())
                .extracting(ClientSupervisor::idPersona).containsExactly(persona);
        assertThat(adapter.findById(idOtro).orElseThrow().getSupervisores())
                .extracting(ClientSupervisor::idPersona).containsExactly(persona);
    }

    @Test
    @DisplayName("se puede buscar por documento")
    void buscarPorDocumento() {
        Client guardado = adapter.save(unCliente());

        assertThat(adapter.findByDocumento(guardado.getDocumento())).isPresent();
        assertThat(adapter.findByDocumento("no-existe")).isEmpty();
    }

    @Test
    @DisplayName("retirar un cliente conserva su fila")
    void retirarCliente() {
        Client guardado = adapter.save(unCliente());
        Client recuperado = adapter.findById(guardado.getId()).orElseThrow();
        recuperado.deactivate();
        adapter.save(recuperado);

        assertThat(adapter.findById(guardado.getId()).orElseThrow().isEstadoActivo()).isFalse();
    }

    @Test
    @DisplayName("el filtrado por dueno encuentra los clientes que una persona supervisa")
    void losClientesDeUnSupervisor() {
        UUID persona = unaPersona();
        Client uno = unCliente();
        uno.appointSupervisor(persona, null);
        Client dos = unCliente();
        dos.appointSupervisor(persona, null);
        UUID ajeno = adapter.save(unCliente()).getId();

        UUID primero = adapter.save(uno).getId();
        UUID segundo = adapter.save(dos).getId();

        // La llave de supervisor_cliente es compuesta a proposito: una persona puede supervisar a
        // varios clientes, y el alcance tiene que traer los dos y ninguno mas.
        assertThat(adapter.findIdsSupervisedBy(persona))
                .containsExactlyInAnyOrder(primero, segundo)
                .doesNotContain(ajeno);
    }

    @Test
    @DisplayName("retirar a un supervisor le quita el acceso, aunque su fila siga ahi")
    void retirarAlSupervisorLeQuitaElAcceso() {
        UUID persona = unaPersona();
        Client cliente = unCliente();
        cliente.appointSupervisor(persona, null);
        Client guardado = adapter.save(cliente);

        Client recuperado = adapter.findById(guardado.getId()).orElseThrow();
        recuperado.removeSupervisor(persona);
        adapter.save(recuperado);

        // Es la razon por la que la consulta mira b_estado_activo del nombramiento: la fila se queda
        // como historial, y si el filtro no la descartara el supervisor retirado seguiria
        // leyendo los datos del cliente.
        assertThat(adapter.findIdsSupervisedBy(persona)).isEmpty();
    }

    @Test
    @DisplayName("quien no supervisa a nadie no recibe ningun cliente")
    void sinNombramientosNingunCliente() {
        adapter.save(unCliente());

        assertThat(adapter.findIdsSupervisedBy(unaPersona())).isEmpty();
        assertThat(adapter.findIdsSupervisedBy(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("un cliente retirado sigue siendo visible para su supervisor")
    void unClienteRetiradoSigueEnElAlcance() {
        UUID persona = unaPersona();
        Client cliente = unCliente();
        cliente.appointSupervisor(persona, null);
        Client guardado = adapter.save(cliente);

        Client recuperado = adapter.findById(guardado.getId()).orElseThrow();
        recuperado.deactivate();
        adapter.save(recuperado);

        // Decision deliberada: el alcance mira si el nombramiento esta activo, no si el cliente lo
        // esta. findAll() devuelve los clientes retirados, y esconderselos solo a su propio
        // supervisor seria incoherente con lo que ve cualquier otro.
        assertThat(adapter.findIdsSupervisedBy(persona)).containsExactly(guardado.getId());
    }
}
