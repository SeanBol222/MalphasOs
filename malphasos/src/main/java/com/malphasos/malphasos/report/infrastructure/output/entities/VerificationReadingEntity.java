package com.malphasos.malphasos.report.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una lectura de la verificación metrológica de un reporte.
 *
 * <p>Tiene identificador propio, a diferencia del puente de la orden de trabajo, que se identifica
 * por el par orden-equipo. Aquí no habría par que sirviera: el punto puede ser nulo —modalidad de
 * patrón y equipo variables— y una clave compuesta con una columna nula no identifica nada. Con
 * identificador propio, corregir una lectura es retirar una fila y escribir otra, y las dos se
 * quedan.
 *
 * <p>El punto de verificación se guarda como identificador aunque pertenezca al módulo de equipos:
 * mapearlo como relación ataría la persistencia de los reportes a la del catálogo.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "dato_verificacion")
public class VerificationReadingEntity {

    @Id
    @Column(name = "k_id_dato_verificacion", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_reporte_servicio", nullable = false)
    private ServiceReportEntity reporte;

    /** Punto en el que se tomó, o nulo si la modalidad no declara puntos. */
    @Column(name = "k_id_punto_verificacion")
    private UUID idPuntoVerificacion;

    @Column(name = "i_secuencia", nullable = false)
    private int secuencia;

    @Column(name = "d_valor_patron", nullable = false)
    private BigDecimal valorPatron;

    @Column(name = "d_valor_equipo", nullable = false)
    private BigDecimal valorEquipo;

    @Column(name = "n_unidad", nullable = false)
    private String unidad;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
