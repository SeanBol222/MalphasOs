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
 * Un paso del protocolo en un reporte: de qué protocolo, en qué estado y su nota. Desde V51 no copia
 * nada del protocolo; su texto lo dice el catálogo o el documento.
 *
 * <p>El protocolo de origen se guarda como identificador aunque la tabla tenga su foránea, por lo mismo
 * que el punto de una lectura: mapearlo como relación ataría la persistencia de los reportes a la del
 * catálogo de equipos.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "paso_reporte")
public class ReportStepEntity {

    @Id
    @Column(name = "k_id_paso_reporte", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_reporte_servicio", nullable = false)
    private ServiceReportEntity reporte;

    @Column(name = "k_id_protocolo", nullable = false)
    private UUID idProtocolo;

    @Column(name = "t_estado_paso", nullable = false)
    private String estado;

    @Column(name = "t_nota")
    private String nota;

    /**
     * Borrado lógico, desde V23, porque el esquema lo tiene en todas sus tablas. Ningún paso se retira
     * hoy —solo cambia de estado—, así que siempre es verdadero y el dominio no lo ve.
     */
    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo = true;
}
