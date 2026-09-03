package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representación JPA de un tipo de equipo.
 *
 * <p>Conserva las <b>dos</b> columnas del esquema, {@code b_verificable} y
 * {@code n_tipo_verificacion}, aunque el agregado solo tenga una: allí lo verificable se deriva de
 * si consta la modalidad. El mapper deriva el booleano al guardar y lo ignora al leer, de modo que
 * las dos columnas no pueden contradecirse. La restricción de la tabla lo respalda.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tipo_equipo")
public class EquipmentTypeEntity {

    @Id
    @Column(name = "k_id_tipo_equipo", nullable = false)
    private UUID id;

    @Column(name = "n_nombre_tipo_equipo", nullable = false, unique = true)
    private String nombre;

    @Column(name = "t_definicion_tecnica", nullable = false)
    private String definicionTecnica;

    @Column(name = "t_recomendaciones_cuidado", nullable = false)
    private String recomendacionesCuidado;

    @Column(name = "t_tecnologia_predominante", nullable = false)
    private String tecnologiaPredominante;

    @Column(name = "i_voltage")
    private Integer voltaje;

    @Column(name = "d_amperaje")
    private BigDecimal amperaje;

    @Column(name = "b_verificable", nullable = false)
    private boolean verificable;

    @Column(name = "n_tipo_verificacion")
    private String tipoVerificacion;

    @Column(name = "m_valor_unitario_mantenimiento", nullable = false)
    private long valorUnitarioMantenimiento;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
