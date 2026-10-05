package com.malphasos.malphasos.equipment.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representación JPA de una unidad que posee un cliente.
 *
 * <p>El área de servicio se guarda como identificador aunque pertenezca a otro módulo: mapearla
 * como relación ataría la persistencia de equipos a la de clientes.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "equipo_cliente")
public class ClientEquipmentEntity {

    @Id
    @Column(name = "k_id_equipo_cliente", nullable = false)
    private UUID id;

    @Column(name = "k_serie", nullable = false)
    private String serie;

    @Column(name = "n_no_inventario")
    private String numeroInventario;

    @Column(name = "f_fecha_compra")
    private LocalDate fechaCompra;

    @Column(name = "v_valor_compra")
    private Long valorCompra;

    @Column(name = "n_codigo_interno")
    private String codigoInterno;

    @Column(name = "n_proveedor")
    private String proveedor;

    // Lo pone el trigger de V20 al insertar, y nunca cambia: ni se inserta ni se actualiza desde aqui.
    // @Generated hace que Hibernate lo relea tras el INSERT; el adaptador hace flush para que ocurra
    // antes de devolver la unidad.
    @Generated(event = EventType.INSERT)
    @Column(name = "n_numero_hoja_vida", insertable = false, updatable = false)
    private String numeroHojaVida;

    @Column(name = "k_id_modelo", nullable = false)
    private UUID idModelo;

    @Column(name = "k_id_area_servicio", nullable = false)
    private UUID idAreaServicio;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
