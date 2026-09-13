package com.malphasos.malphasos.workorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.Payload;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.SelectedEquipment;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderAssignedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderCreatedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderDeactivatedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderEquipmentAddedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderEquipmentRemovedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderExecutedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderStartedEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Invariantes del agregado de órdenes de trabajo, sin base de datos.
 *
 * <p>Se prueba lo que el agregado puede decidir por sí solo. Que la sede sea del cliente, que el
 * equipo estuviera en el área declarada o que el ingeniero sea de tipo {@code ENGINEER} exigen
 * consultar otros módulos y se verificarán con el servicio de aplicación.
 */
class WorkOrderTest {

    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID SEDE = UUID.randomUUID();
    private static final UUID INGENIERO = UUID.randomUUID();
    private static final LocalDate MANANA = LocalDate.now().plusDays(1);

    private static WorkOrder unaOrden() {
        return WorkOrder.schedule(
                CLIENTE, SEDE, MANANA, Periodicity.TRIMESTRAL, ServiceType.PREVENTIVO);
    }

    /** Una orden lista para arrancar: con ingeniero y con un equipo. */
    private static WorkOrder unaOrdenArrancable() {
        WorkOrder orden = unaOrden();
        orden.assignTo(INGENIERO);
        orden.addEquipment(UUID.randomUUID(), UUID.randomUUID());
        orden.pullEvents();

        return orden;
    }

    private static List<String> tiposDe(WorkOrder orden) {
        return orden.pullEvents().stream()
                .map(DomainEvent::metadata)
                .map(metadata -> metadata.eventType())
                .toList();
    }

    @Nested
    @DisplayName("Al programarla")
    class AlProgramarla {

        @Test
        @DisplayName("nace creada, sin ingeniero y sin equipos")
        void naceCreada() {
            WorkOrder orden = unaOrden();

            assertThat(orden.getEstadoEjecucion()).isEqualTo(ExecutionState.CREADA);
            assertThat(orden.getIdIngeniero()).isNull();
            assertThat(orden.getEquipos()).isEmpty();
            assertThat(orden.isEstadoActivo()).isTrue();
        }

        @Test
        @DisplayName("anuncia que se programo un mantenimiento")
        void anunciaElAlta() {
            assertThat(tiposDe(unaOrden())).containsExactly(WorkOrderCreatedEvent.TYPE);
        }

        @Test
        @DisplayName("el cliente y la sede son obligatorios")
        void exigeClienteYSede() {
            assertThatIllegalArgumentException().isThrownBy(() -> WorkOrder.schedule(
                    null, SEDE, MANANA, Periodicity.ANUAL, ServiceType.PREVENTIVO));
            assertThatIllegalArgumentException().isThrownBy(() -> WorkOrder.schedule(
                    CLIENTE, null, MANANA, Periodicity.ANUAL, ServiceType.PREVENTIVO));
        }

        @Test
        @DisplayName("la fecha, la periodicidad y el tipo de servicio son obligatorios")
        void exigeElRestoDeCampos() {
            assertThatIllegalArgumentException().isThrownBy(() -> WorkOrder.schedule(
                    CLIENTE, SEDE, null, Periodicity.ANUAL, ServiceType.PREVENTIVO));
            assertThatIllegalArgumentException().isThrownBy(() -> WorkOrder.schedule(
                    CLIENTE, SEDE, MANANA, null, ServiceType.PREVENTIVO));
            assertThatIllegalArgumentException().isThrownBy(() -> WorkOrder.schedule(
                    CLIENTE, SEDE, MANANA, Periodicity.ANUAL, null));
        }

        @Test
        @DisplayName("la fecha puede estar en el futuro y en el pasado, al reves que la de compra")
        void noAcotaLaFecha() {
            // Un mantenimiento se programa —futuro— y a veces se registra despues de haber ido
            // —pasado—. Ninguno de los dos extremos es un error, a diferencia de la fecha de
            // compra de un equipo, que si rechaza el futuro.
            assertThatCode(() -> WorkOrder.schedule(CLIENTE, SEDE, LocalDate.now().plusYears(1),
                    Periodicity.ANUAL, ServiceType.PREVENTIVO)).doesNotThrowAnyException();
            assertThatCode(() -> WorkOrder.schedule(CLIENTE, SEDE, LocalDate.now().minusYears(1),
                    Periodicity.ANUAL, ServiceType.PREVENTIVO)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rehidratar no emite ningun evento")
        void rehidratarNoEmite() {
            // Leer de la base no es un hecho del dominio.
            WorkOrder orden = WorkOrder.rehydrate(UUID.randomUUID(), CLIENTE, SEDE, MANANA,
                    Periodicity.MENSUAL, ServiceType.CALIBRACION, ExecutionState.EN_EJECUCION,
                    INGENIERO, Set.of(), true);

            assertThat(orden.hasPendingEvents()).isFalse();
        }
    }

    @Nested
    @DisplayName("El alcance")
    class ElAlcance {

        @Test
        @DisplayName("un equipo entra con el area en la que estaba al seleccionarlo")
        void entraConSuArea() {
            WorkOrder orden = unaOrden();
            UUID equipo = UUID.randomUUID();
            UUID area = UUID.randomUUID();

            orden.addEquipment(equipo, area);

            assertThat(orden.getEquipos())
                    .extracting(SelectedEquipment::getIdEquipoCliente,
                            SelectedEquipment::getIdAreaServicio)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(equipo, area));
        }

        @Test
        @DisplayName("anadir dos veces el mismo equipo no lo duplica ni emite dos eventos")
        void anadirDosVecesEsIdempotente() {
            WorkOrder orden = unaOrden();
            UUID equipo = UUID.randomUUID();
            UUID area = UUID.randomUUID();

            orden.addEquipment(equipo, area);
            orden.addEquipment(equipo, area);

            assertThat(orden.getEquipos()).hasSize(1);
            assertThat(tiposDe(orden)).containsExactly(
                    WorkOrderCreatedEvent.TYPE, WorkOrderEquipmentAddedEvent.TYPE);
        }

        @Test
        @DisplayName("reanadir el mismo equipo con otra area NO cambia el area congelada")
        void reanadirNoCambiaElAreaCongelada() {
            // Es contraintuitivo y por eso se fija: dentro de una orden la identidad de un equipo
            // es el equipo, no el par. Para corregir el area hay que retirarlo y volver a anadirlo.
            WorkOrder orden = unaOrden();
            UUID equipo = UUID.randomUUID();
            UUID areaOriginal = UUID.randomUUID();
            UUID areaNueva = UUID.randomUUID();

            orden.addEquipment(equipo, areaOriginal);
            orden.addEquipment(equipo, areaNueva);

            assertThat(orden.getEquipos()).singleElement()
                    .extracting(SelectedEquipment::getIdAreaServicio)
                    .isEqualTo(areaOriginal);
        }

        @Test
        @DisplayName("retirar y volver a anadir si permite corregir el area")
        void retirarYReanadirCorrigeElArea() {
            WorkOrder orden = unaOrden();
            UUID equipo = UUID.randomUUID();
            UUID areaNueva = UUID.randomUUID();

            orden.addEquipment(equipo, UUID.randomUUID());
            orden.removeEquipment(equipo);
            orden.addEquipment(equipo, areaNueva);

            assertThat(orden.getEquipos()).singleElement()
                    .extracting(SelectedEquipment::getIdAreaServicio)
                    .isEqualTo(areaNueva);
        }

        @Test
        @DisplayName("retirar un equipo que no esta no hace nada")
        void retirarLoQueNoEstaNoHaceNada() {
            WorkOrder orden = unaOrden();
            orden.pullEvents();

            orden.removeEquipment(UUID.randomUUID());

            assertThat(orden.hasPendingEvents()).isFalse();
        }

        @Test
        @DisplayName("retirar anuncia el equipo que salio, con su area")
        void retirarAnunciaElEquipo() {
            WorkOrder orden = unaOrden();
            UUID equipo = UUID.randomUUID();
            orden.addEquipment(equipo, UUID.randomUUID());
            orden.pullEvents();

            orden.removeEquipment(equipo);

            assertThat(tiposDe(orden)).containsExactly(WorkOrderEquipmentRemovedEvent.TYPE);
            assertThat(orden.getEquipos()).isEmpty();
        }

        @Test
        @DisplayName("el equipo y su area son obligatorios")
        void exigeEquipoYArea() {
            WorkOrder orden = unaOrden();

            assertThat(catchNullPointer(() -> orden.addEquipment(null, UUID.randomUUID())))
                    .isTrue();
            assertThat(catchNullPointer(() -> orden.addEquipment(UUID.randomUUID(), null)))
                    .isTrue();
        }

        @Test
        @DisplayName("la coleccion que se devuelve es una copia inmutable")
        void laColeccionEsInmutable() {
            WorkOrder orden = unaOrden();
            orden.addEquipment(UUID.randomUUID(), UUID.randomUUID());

            Set<SelectedEquipment> copia = orden.getEquipos();

            assertThatCode(() -> copia.clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(orden.getEquipos()).hasSize(1);
        }

        private static boolean catchNullPointer(Runnable accion) {
            try {
                accion.run();
                return false;
            } catch (NullPointerException e) {
                return true;
            }
        }
    }

    @Nested
    @DisplayName("La asignacion")
    class LaAsignacion {

        @Test
        @DisplayName("poner la orden en manos de un ingeniero lo anuncia")
        void asignarAnuncia() {
            WorkOrder orden = unaOrden();
            orden.pullEvents();

            orden.assignTo(INGENIERO);

            assertThat(orden.getIdIngeniero()).isEqualTo(INGENIERO);
            assertThat(tiposDe(orden)).containsExactly(WorkOrderAssignedEvent.TYPE);
        }

        @Test
        @DisplayName("reasignar al mismo ingeniero no emite un segundo evento")
        void reasignarAlMismoNoEmite() {
            WorkOrder orden = unaOrden();
            orden.assignTo(INGENIERO);
            orden.pullEvents();

            orden.assignTo(INGENIERO);

            assertThat(orden.hasPendingEvents()).isFalse();
        }

        @Test
        @DisplayName("reasignar a otro ingeniero si es un hecho nuevo")
        void reasignarAOtroSiEmite() {
            WorkOrder orden = unaOrden();
            orden.assignTo(INGENIERO);
            orden.pullEvents();
            UUID otro = UUID.randomUUID();

            orden.assignTo(otro);

            assertThat(orden.getIdIngeniero()).isEqualTo(otro);
            assertThat(tiposDe(orden)).containsExactly(WorkOrderAssignedEvent.TYPE);
        }

        @Test
        @DisplayName("el ingeniero es obligatorio")
        void exigeIngeniero() {
            WorkOrder orden = unaOrden();

            assertThatIllegalArgumentException().isThrownBy(() -> orden.assignTo(null));
        }
    }

    @Nested
    @DisplayName("El ciclo de vida")
    class ElCicloDeVida {

        @Test
        @DisplayName("una orden con ingeniero y equipos arranca")
        void arranca() {
            WorkOrder orden = unaOrdenArrancable();

            orden.start();

            assertThat(orden.getEstadoEjecucion()).isEqualTo(ExecutionState.EN_EJECUCION);
            assertThat(tiposDe(orden)).containsExactly(WorkOrderStartedEvent.TYPE);
        }

        @Test
        @DisplayName("sin ingeniero no arranca: no hay quien lo haga")
        void sinIngenieroNoArranca() {
            WorkOrder orden = unaOrden();
            orden.addEquipment(UUID.randomUUID(), UUID.randomUUID());

            assertThatIllegalStateException().isThrownBy(orden::start)
                    .withMessageContaining("sin ingeniero");
        }

        @Test
        @DisplayName("sin equipos no arranca: no hay sobre que")
        void sinEquiposNoArranca() {
            WorkOrder orden = unaOrden();
            orden.assignTo(INGENIERO);

            assertThatIllegalStateException().isThrownBy(orden::start)
                    .withMessageContaining("sin equipos");
        }

        @Test
        @DisplayName("un fallo al arrancar no deja la orden a medias")
        void elFalloNoDejaEstadoRoto() {
            // La comprobacion va antes de mutar: si arrancar falla, la orden sigue CREADA y sin
            // ningun evento pendiente que alguien pudiera publicar.
            WorkOrder orden = unaOrden();
            orden.assignTo(INGENIERO);
            orden.pullEvents();

            assertThatIllegalStateException().isThrownBy(orden::start);

            assertThat(orden.getEstadoEjecucion()).isEqualTo(ExecutionState.CREADA);
            assertThat(orden.hasPendingEvents()).isFalse();
        }

        @Test
        @DisplayName("no se salta de creada a ejecutada")
        void noSeSaltaElPasoIntermedio() {
            WorkOrder orden = unaOrdenArrancable();

            assertThatIllegalStateException().isThrownBy(orden::execute)
                    .withMessageContaining("EN_EJECUCION");
        }

        @Test
        @DisplayName("una orden ejecutada no vuelve a ejecucion ni se ejecuta dos veces")
        void ejecutadaEsFinal() {
            WorkOrder orden = unaOrdenArrancable();
            orden.start();
            orden.execute();

            assertThatIllegalStateException().isThrownBy(orden::start)
                    .withMessageContaining("ya ejecutada");
            assertThatIllegalStateException().isThrownBy(orden::execute)
                    .withMessageContaining("ya ejecutada");
        }

        @Test
        @DisplayName("el alcance se puede tocar en ejecucion, porque en campo cambia")
        void enEjecucionElAlcanceSeToca() {
            WorkOrder orden = unaOrdenArrancable();
            orden.start();
            orden.pullEvents();

            orden.addEquipment(UUID.randomUUID(), UUID.randomUUID());

            assertThat(orden.getEquipos()).hasSize(2);
            assertThat(tiposDe(orden)).containsExactly(WorkOrderEquipmentAddedEvent.TYPE);
        }

        @Test
        @DisplayName("una orden ejecutada ya no admite cambios de alcance ni de ingeniero")
        void ejecutadaNoAdmiteCambios() {
            WorkOrder orden = unaOrdenArrancable();
            orden.start();
            orden.execute();

            assertThatIllegalStateException()
                    .isThrownBy(() -> orden.addEquipment(UUID.randomUUID(), UUID.randomUUID()));
            assertThatIllegalStateException()
                    .isThrownBy(() -> orden.removeEquipment(UUID.randomUUID()));
            assertThatIllegalStateException()
                    .isThrownBy(() -> orden.assignTo(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("La cancelacion")
    class LaCancelacion {

        @Test
        @DisplayName("cancelar retira la orden sin borrarla")
        void cancelarRetira() {
            WorkOrder orden = unaOrden();
            orden.pullEvents();

            orden.cancel();

            assertThat(orden.isEstadoActivo()).isFalse();
            assertThat(tiposDe(orden)).containsExactly(WorkOrderDeactivatedEvent.TYPE);
        }

        @Test
        @DisplayName("cancelar dos veces no emite dos eventos")
        void cancelarEsIdempotente() {
            WorkOrder orden = unaOrden();
            orden.cancel();
            orden.pullEvents();

            orden.cancel();

            assertThat(orden.hasPendingEvents()).isFalse();
        }

        @Test
        @DisplayName("una orden ya ejecutada si se puede cancelar")
        void ejecutadaSePuedeCancelar() {
            // Cancelar no es lo contrario de ejecutar: retirar del listado un registro historico
            // no reescribe lo que ocurrio.
            WorkOrder orden = unaOrdenArrancable();
            orden.start();
            orden.execute();
            orden.pullEvents();

            orden.cancel();

            assertThat(orden.isEstadoActivo()).isFalse();
            assertThat(tiposDe(orden)).containsExactly(WorkOrderDeactivatedEvent.TYPE);
        }

        @Test
        @DisplayName("una orden cancelada no admite ninguna otra operacion")
        void canceladaNoAdmiteNada() {
            WorkOrder orden = unaOrdenArrancable();
            orden.cancel();

            assertThatIllegalStateException().isThrownBy(orden::start)
                    .withMessageContaining("cancelada");
            assertThatIllegalStateException()
                    .isThrownBy(() -> orden.assignTo(UUID.randomUUID()))
                    .withMessageContaining("cancelada");
            assertThatIllegalStateException()
                    .isThrownBy(() -> orden.addEquipment(UUID.randomUUID(), UUID.randomUUID()))
                    .withMessageContaining("cancelada");
        }
    }

    @Nested
    @DisplayName("Las transiciones, en el enum")
    class LasTransiciones {

        @Test
        @DisplayName("el recorrido es creada, en ejecucion, ejecutada")
        void elRecorrido() {
            assertThat(ExecutionState.CREADA.siguiente()).isEqualTo(ExecutionState.EN_EJECUCION);
            assertThat(ExecutionState.EN_EJECUCION.siguiente()).isEqualTo(ExecutionState.EJECUTADA);
            assertThat(ExecutionState.EJECUTADA.siguiente()).isNull();
        }

        @Test
        @DisplayName("solo se avanza al inmediato siguiente, nunca hacia atras")
        void soloAvanzaAlSiguiente() {
            assertThat(ExecutionState.CREADA.avanzaA(ExecutionState.EN_EJECUCION)).isTrue();
            assertThat(ExecutionState.CREADA.avanzaA(ExecutionState.EJECUTADA)).isFalse();
            assertThat(ExecutionState.CREADA.avanzaA(ExecutionState.CREADA)).isFalse();
            assertThat(ExecutionState.EN_EJECUCION.avanzaA(ExecutionState.CREADA)).isFalse();
            assertThat(ExecutionState.EJECUTADA.avanzaA(ExecutionState.EN_EJECUCION)).isFalse();
            assertThat(ExecutionState.EJECUTADA.avanzaA(null)).isFalse();
        }

        @Test
        @DisplayName("solo ejecutada es final")
        void soloEjecutadaEsFinal() {
            assertThat(ExecutionState.CREADA.esFinal()).isFalse();
            assertThat(ExecutionState.EN_EJECUCION.esFinal()).isFalse();
            assertThat(ExecutionState.EJECUTADA.esFinal()).isTrue();
        }
    }

    @Nested
    @DisplayName("El contrato de los eventos")
    class ElContratoDeLosEventos {

        @Test
        @DisplayName("recoger los eventos vacia la lista")
        void recogerVacia() {
            WorkOrder orden = unaOrden();

            assertThat(orden.pullEvents()).hasSize(1);
            assertThat(orden.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("todo evento lleva el identificador de la orden que lo produjo")
        void llevanElIdentificador() {
            WorkOrder orden = unaOrdenArrancable();
            orden.start();
            orden.execute();

            List<DomainEvent<? extends Payload>> eventos = orden.pullEvents();

            assertThat(eventos).isNotEmpty();
            assertThat(eventos)
                    .allSatisfy(evento -> assertThat(evento.metadata().aggregateId())
                            .isEqualTo(orden.getId().toString()));
        }

        @Test
        @DisplayName("los tipos son los que el resto del sistema espera")
        void losTiposSonEstables() {
            // Un consumidor externo se suscribe por este nombre: cambiarlo lo rompe en silencio.
            assertThat(WorkOrderCreatedEvent.TYPE).isEqualTo("work-order.created");
            assertThat(WorkOrderAssignedEvent.TYPE).isEqualTo("work-order.assigned");
            assertThat(WorkOrderStartedEvent.TYPE).isEqualTo("work-order.started");
            assertThat(WorkOrderExecutedEvent.TYPE).isEqualTo("work-order.executed");
            assertThat(WorkOrderDeactivatedEvent.TYPE).isEqualTo("work-order.deactivated");
            assertThat(WorkOrderEquipmentAddedEvent.TYPE).isEqualTo("work-order.equipment-added");
            assertThat(WorkOrderEquipmentRemovedEvent.TYPE)
                    .isEqualTo("work-order.equipment-removed");
        }
    }

    @Nested
    @DisplayName("La identidad")
    class LaIdentidad {

        @Test
        @DisplayName("dos ordenes con los mismos datos son distintas")
        void laIdentidadNoSonLosDatos() {
            assertThat(unaOrden()).isNotEqualTo(unaOrden());
        }

        @Test
        @DisplayName("una orden sigue siendo ella misma aunque cambien sus datos")
        void laIdentidadSobreviveAlCambio() {
            WorkOrder orden = unaOrdenArrancable();
            WorkOrder mismaOrden = WorkOrder.rehydrate(orden.getId(), CLIENTE, SEDE, MANANA,
                    Periodicity.ANUAL, ServiceType.CORRECTIVO, ExecutionState.EJECUTADA,
                    UUID.randomUUID(), Set.of(), false);

            assertThat(mismaOrden).isEqualTo(orden);
            assertThat(mismaOrden).hasSameHashCodeAs(orden);
        }

        @Test
        @DisplayName("un equipo seleccionado se identifica por el equipo, no por el par")
        void laIdentidadDelSeleccionado() {
            UUID equipo = UUID.randomUUID();

            assertThat(SelectedEquipment.of(equipo, UUID.randomUUID()))
                    .isEqualTo(SelectedEquipment.of(equipo, UUID.randomUUID()));
        }
    }
}
