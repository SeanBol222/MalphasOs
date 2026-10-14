package com.malphasos.malphasos.equipment.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.protocol.AssignedProtocol;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment.Requested;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Los protocolos contra PostgreSQL real: lo que V21 impide, y que reordenar no choca con su propio
 * índice único, que es la razón de que la asignación se guarde con SQL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM protocolo_tipo_equipo",
            "DELETE FROM protocolo",
            "DELETE FROM tipo_equipo"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ProtocolPersistenceTest {

    @Autowired private ProtocolPersistenceAdapter protocolos;
    @Autowired private ProtocolAssignmentPersistenceAdapter asignaciones;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID unTipo() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                id, "Tipo " + System.nanoTime());

        return id;
    }

    private Protocol unProtocolo(String nombre) {
        return protocolos.save(Protocol.create(nombre + " " + System.nanoTime(), "Descripción"));
    }

    @Nested
    @DisplayName("El catálogo")
    class Catalogo {

        @Test
        @DisplayName("ida y vuelta, con la clave calculada guardada")
        void idaYVuelta() {
            Protocol protocolo = protocolos.save(Protocol.create("Limpieza y Desinfección", "Paño."));

            assertThat(protocolos.findById(protocolo.getId())).get()
                    .extracting(Protocol::getNombre, Protocol::getDescripcion, Protocol::isEstadoActivo)
                    .containsExactly("Limpieza y Desinfección", "Paño.", true);
            assertThat(protocolos.findActiveByClave("limpieza y desinfeccion")).get()
                    .extracting(Protocol::getId).isEqualTo(protocolo.getId());
        }

        @Test
        @DisplayName("la base impide dos vigentes con la misma clave, aunque el servicio se salte la comprobación")
        void claveUnicaEntreVigentes() {
            protocolos.save(Protocol.create("Limpieza", "Uno"));

            assertThatThrownBy(() -> protocolos.save(Protocol.create("LIMPIEZA", "Dos")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("uno retirado no reserva su nombre")
        void retiradoLibera() {
            Protocol viejo = Protocol.create("Limpieza", "Uno");
            viejo.deactivate();
            protocolos.save(viejo);

            Protocol nuevo = protocolos.save(Protocol.create("Limpieza", "Dos"));

            assertThat(protocolos.findActiveByClave("limpieza")).get()
                    .extracting(Protocol::getId).isEqualTo(nuevo.getId());
        }

        @Test
        @DisplayName("la clave en mayúsculas no entra: la base exige la forma que calcula el dominio")
        void claveEnMinusculas() {
            assertThatThrownBy(() -> jdbcTemplate.update(
                            "INSERT INTO protocolo (k_id_protocolo, n_nombre, n_clave, t_descripcion) VALUES (?, 'X', 'X', 'Y')",
                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("cuenta los tipos vigentes que lo usan, y solo esos")
        void cuentaUsos() {
            Protocol limpieza = unProtocolo("Limpieza");
            UUID vigente = unTipo();
            UUID retirado = unTipo();
            jdbcTemplate.update("UPDATE tipo_equipo SET b_estado_activo = false WHERE k_id_tipo_equipo = ?", retirado);

            for (UUID tipo : List.of(vigente, retirado)) {
                ProtocolAssignment asignacion = ProtocolAssignment.empty(tipo);
                asignacion.assign(List.of(new Requested(limpieza.getId(), null)));
                asignaciones.save(asignacion);
            }

            assertThat(protocolos.countActiveAssignments(limpieza.getId())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("La asignación a un tipo")
    class Asignacion {

        @Test
        @DisplayName("un tipo sin protocolos tiene una lista vacía, no «no tiene asignación»")
        void vacia() {
            assertThat(asignaciones.findByType(unTipo()).getProtocolos()).isEmpty();
        }

        @Test
        @DisplayName("se guarda en orden de prioridad, con su indicación")
        void guarda() {
            UUID tipo = unTipo();
            Protocol a = unProtocolo("A");
            Protocol b = unProtocolo("B");

            ProtocolAssignment asignacion = ProtocolAssignment.empty(tipo);
            asignacion.assign(List.of(new Requested(b.getId(), "Con guantes"), new Requested(a.getId(), null)));

            assertThat(asignaciones.save(asignacion).getProtocolos()).containsExactly(
                    new AssignedProtocol(b.getId(), 1, "Con guantes"), new AssignedProtocol(a.getId(), 2, null));
        }

        @Test
        @DisplayName("las vigentes de todos los tipos salen juntas, cada tipo en su orden y sin las quitadas")
        void todasLasVigentes() {
            UUID conDos = unTipo();
            UUID conUnaQuitada = unTipo();
            UUID sinNada = unTipo();
            Protocol a = unProtocolo("A");
            Protocol b = unProtocolo("B");

            ProtocolAssignment dos = ProtocolAssignment.empty(conDos);
            dos.assign(List.of(new Requested(b.getId(), "Con guantes"), new Requested(a.getId(), null)));
            asignaciones.save(dos);
            ProtocolAssignment unaQuitada = ProtocolAssignment.empty(conUnaQuitada);
            unaQuitada.assign(List.of(new Requested(a.getId(), null)));
            asignaciones.save(unaQuitada);
            ProtocolAssignment vacia = asignaciones.findByType(conUnaQuitada);
            vacia.assign(List.of());
            asignaciones.save(vacia);

            List<ProtocolAssignment> todas = asignaciones.findAllActive();

            // El tipo al que se le quitó todo y el que nunca tuvo no aparecen.
            assertThat(todas).extracting(ProtocolAssignment::getIdTipoEquipo).containsExactly(conDos);
            assertThat(todas.getFirst().getProtocolos()).containsExactly(
                    new AssignedProtocol(b.getId(), 1, "Con guantes"), new AssignedProtocol(a.getId(), 2, null));
            assertThat(sinNada).isNotNull();
        }

        @Test
        @DisplayName("invertir el orden no choca con el índice único de prioridad")
        void reordenarSinChocar() {
            // Es el caso que obliga a guardar en dos pasos: fila a fila, mover la 2 a la 1 mientras la 1
            // sigue siendo la 1 viola UQ_protocolo_tipo_equipo_prioridad_activa a mitad de camino.
            UUID tipo = unTipo();
            Protocol a = unProtocolo("A");
            Protocol b = unProtocolo("B");
            Protocol c = unProtocolo("C");

            ProtocolAssignment asignacion = ProtocolAssignment.empty(tipo);
            asignacion.assign(List.of(new Requested(a.getId(), null), new Requested(b.getId(), null),
                    new Requested(c.getId(), null)));
            asignaciones.save(asignacion);

            ProtocolAssignment leida = asignaciones.findByType(tipo);
            leida.assign(List.of(new Requested(c.getId(), null), new Requested(b.getId(), null),
                    new Requested(a.getId(), null)));

            assertThat(asignaciones.save(leida).getProtocolos())
                    .extracting(AssignedProtocol::idProtocolo)
                    .containsExactly(c.getId(), b.getId(), a.getId());
        }

        @Test
        @DisplayName("quitar uno lo deja retirado y no lo borra; volver a ponerlo lo reactiva")
        void quitarYVolver() {
            UUID tipo = unTipo();
            Protocol a = unProtocolo("A");
            Protocol b = unProtocolo("B");

            ProtocolAssignment asignacion = ProtocolAssignment.empty(tipo);
            asignacion.assign(List.of(new Requested(a.getId(), null), new Requested(b.getId(), null)));
            asignaciones.save(asignacion);

            ProtocolAssignment sinA = asignaciones.findByType(tipo);
            sinA.assign(List.of(new Requested(b.getId(), null)));
            assertThat(asignaciones.save(sinA).getProtocolos())
                    .containsExactly(new AssignedProtocol(b.getId(), 1, null));
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM protocolo_tipo_equipo WHERE k_id_tipo_equipo = ?", Integer.class, tipo))
                    .isEqualTo(2);

            ProtocolAssignment otraVez = asignaciones.findByType(tipo);
            otraVez.assign(List.of(new Requested(a.getId(), "Ahora con guantes"), new Requested(b.getId(), null)));
            assertThat(asignaciones.save(otraVez).getProtocolos())
                    .containsExactly(new AssignedProtocol(a.getId(), 1, "Ahora con guantes"),
                            new AssignedProtocol(b.getId(), 2, null));
        }

        @Test
        @DisplayName("la base impide dos vigentes con la misma prioridad en un tipo, y prioridades no positivas")
        void restriccionesDePrioridad() {
            UUID tipo = unTipo();
            Protocol a = unProtocolo("A");
            Protocol b = unProtocolo("B");
            String insertar = "INSERT INTO protocolo_tipo_equipo (k_id_tipo_equipo, k_id_protocolo, i_prioridad)"
                    + " VALUES (?, ?, ?)";
            jdbcTemplate.update(insertar, tipo, a.getId(), 1);

            assertThatThrownBy(() -> jdbcTemplate.update(insertar, tipo, b.getId(), 1))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(insertar, tipo, b.getId(), 0))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("una indicación en blanco no entra: o tiene texto o es nula")
        void indicacionEnBlanco() {
            UUID tipo = unTipo();
            Protocol a = unProtocolo("A");

            assertThatThrownBy(() -> jdbcTemplate.update(
                            "INSERT INTO protocolo_tipo_equipo (k_id_tipo_equipo, k_id_protocolo, i_prioridad, t_indicacion)"
                                    + " VALUES (?, ?, 1, '  ')",
                            tipo, a.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
