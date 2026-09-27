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
 * <p>Cuelga de su tipo de equipo y no tiene vida propia: no hay repositorio ni adaptador para ella, se
 * guarda y se lee con el tipo. Es la misma forma que los contactos de un cliente.
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
    @JoinColumn(name = "k_id_tipo_equipo", nullable = false)
    private EquipmentTypeEntity tipoEquipo;

    @Column(name = "d_valor", nullable = false, precision = 12, scale = 4)
    private BigDecimal valor;

    @Column(name = "n_unidad", nullable = false, length = 20)
    private String unidad;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
