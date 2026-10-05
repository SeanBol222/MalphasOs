package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lo que la capa de aplicación necesita de un almacén de intervenciones.
 *
 * <p>Sin {@code delete} y sin {@code update} separado. Una intervención no se modifica en lo que
 * describe; lo único que se le escribe después de registrarla es que <b>quedó reemplazada</b> por la
 * del reporte que la corrigió, y eso va por {@code save}, como en el resto del proyecto. (Este
 * comentario decía que «no se modifica nunca»: cierto hasta el 2026-10-04, cuando entró el reemplazo.)
 */
public interface InterventionPersistencePort {

    /** El historial de un equipo, de lo más reciente a lo más antiguo. */
    List<Intervention> findByEquipment(UUID idEquipoCliente);

    /** Si ese reporte ya tiene su línea en el historial. Sostiene la idempotencia del registro. */
    boolean existsByReport(UUID idReporteServicio);

    /** Las intervenciones de estos reportes, vigentes o no. Sostiene el reemplazo de una corrección. */
    List<Intervention> findByReports(Collection<UUID> idsReportes);

    Intervention save(Intervention intervention);
}
