package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representación JPA de un punto de verificación.
 *
 * <p><b>Cuelga de su verificación y no del tipo de equipo</b> desde el 2026-10-03, y ya no guarda la
 * unidad: la declara la verificación. Un punto de 50 no significa nada suelto en un aparato que mide
 * presión y temperatura.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "punto_verificacion")
public class VerificationPointEntity {

    @Id
    @Column(name = "k_id_punto_verificacion", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "k_id_verificacion", nullable = false)
    private TypeVerificationEntity verificacion;

    @Column(name = "d_valor", nullable = false, precision = 12, scale = 4)
    private BigDecimal valor;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
