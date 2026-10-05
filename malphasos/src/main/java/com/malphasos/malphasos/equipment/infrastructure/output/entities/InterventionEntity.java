package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una línea del historial de intervenciones de un equipo.
 *
 * <p>Las referencias al equipo y al reporte van como UUID y no como {@code @ManyToOne}: el proyecto
 * referencia entre agregados por identificador, y aquí hay una razón más: el reporte es de otro
 * módulo —{@code report}, que está aguas abajo— y mapearlo como relación habría metido su entidad en
 * este lado del grafo.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "intervencion")
public class InterventionEntity {

    @Id
    @Column(name = "k_id_intervencion", nullable = false)
    private UUID id;

    @Column(name = "k_id_equipo_cliente", nullable = false)
    private UUID idEquipoCliente;

    @Column(name = "k_id_reporte_servicio", nullable = false)
    private UUID idReporteServicio;

    @Column(name = "f_fecha_servicio", nullable = false)
    private LocalDateTime fechaServicio;

    @Column(name = "t_tipo_servicio", nullable = false, length = 11)
    private String tipoServicio;

    @Column(name = "t_resultado", nullable = false, length = 27)
    private String resultado;

    @Column(name = "t_descripcion", length = 500)
    private String descripcion;

    @Column(name = "n_responsable", length = 250)
    private String responsable;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    /** La línea que sustituyó a esta, cuando su reporte se corrigió. Nula si está vigente. */
    @Column(name = "k_id_reemplazada_por")
    private UUID reemplazadaPor;
}
