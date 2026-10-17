package com.malphasos.malphasos.report.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un accesorio con el que llegó el equipo al servicio (V31). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "accesorio_reporte")
public class ReportAccessoryEntity {

    @Id
    @Column(name = "k_id_accesorio_reporte", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_reporte_servicio", nullable = false)
    private ServiceReportEntity reporte;

    @Column(name = "i_orden", nullable = false)
    private int orden;

    @Column(name = "n_nombre", nullable = false)
    private String nombre;

    @Column(name = "b_conforme", nullable = false)
    private boolean conforme;

    @Column(name = "t_observacion")
    private String observacion;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
