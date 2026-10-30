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

/**
 * Un patrón usado en una verificación de un reporte, con la copia de lo que el catálogo decía de él
 * (V30). La verificación y el patrón se guardan como identificadores, por lo mismo que el punto de una
 * lectura: mapearlos como relación ataría la persistencia de los reportes a la del catálogo de equipos.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "patron_verificacion_reporte")
public class VerificationStandardEntity {

    @Id
    @Column(name = "k_id_patron_verificacion_reporte", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_reporte_servicio", nullable = false)
    private ServiceReportEntity reporte;

    @Column(name = "k_id_verificacion", nullable = false)
    private UUID idVerificacion;

    @Column(name = "k_id_equipo_patron", nullable = false)
    private UUID idEquipoPatron;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
