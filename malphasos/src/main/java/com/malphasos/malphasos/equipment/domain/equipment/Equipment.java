package com.malphasos.malphasos.equipment.domain.equipment;

import com.malphasos.malphasos.equipment.domain.equipment.events.EquipmentCreatedEvent;
import com.malphasos.malphasos.equipment.domain.equipment.events.EquipmentDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.equipment.events.EquipmentPayload;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * La asociación entre una marca y un tipo de equipo: qué tipos fabrica cada marca.
 *
 * <p>No es un equipo físico —eso es {@code ClientEquipment}— ni tiene atributo alguno propio. El
 * nombre viene de la tabla {@code equipo} del esquema original y se conserva para no divergir del
 * sistema del que se migra.
 *
 * <p><b>Sus dos referencias no cambian.</b> Si una asociación resulta equivocada se retira y se crea
 * la correcta: cambiarla de tipo o de marca convertiría en mentira todos los modelos que ya cuelgan
 * de ella. El original ofrecía {@code updateEquipment} y {@code updateEquipmentPatch} para hacer
 * justamente eso.
 *
 * <p>Guardaba además cada referencia <b>dos veces</b>, como identificador y como objeto completo
 * —{@code brandId} junto a {@code brand}—, sin nada que mantuviera ambos al día. Aquí solo hay
 * identificadores, como en el resto del proyecto.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Equipment extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private final UUID idTipoEquipo;

    private final UUID idMarca;

    private boolean estadoActivo;

    private Equipment(UUID id, UUID idTipoEquipo, UUID idMarca, boolean estadoActivo) {
        this.id = id;
        this.idTipoEquipo = idTipoEquipo;
        this.idMarca = idMarca;
        this.estadoActivo = estadoActivo;
    }

    public static Equipment create(UUID idTipoEquipo, UUID idMarca) {
        Equipment equipo = new Equipment(
                UUID.randomUUID(),
                exigir(idTipoEquipo, "tipo de equipo"),
                exigir(idMarca, "marca"),
                true);

        equipo.registerEvent(new EquipmentCreatedEvent(
                equipo.metadataFor(EquipmentCreatedEvent.TYPE), equipo.payload()));

        return equipo;
    }

    public static Equipment rehydrate(UUID id, UUID idTipoEquipo, UUID idMarca, boolean estadoActivo) {
        return new Equipment(id, idTipoEquipo, idMarca, estadoActivo);
    }

    /** Retira la asociación sin borrarla. Retirar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new EquipmentDeactivatedEvent(
                metadataFor(EquipmentDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "Equipment";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private EquipmentPayload payload() {
        return new EquipmentPayload(idTipoEquipo, idMarca);
    }

    private static UUID exigir(UUID valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("La asociacion necesita " + campo);
        }

        return valor;
    }
}
