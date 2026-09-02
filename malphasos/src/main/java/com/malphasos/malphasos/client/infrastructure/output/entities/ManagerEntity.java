package com.malphasos.malphasos.client.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representación JPA de un encargado.
 *
 * <p>La llave primaria es la de la persona, y en el esquema es a la vez clave foránea hacia
 * {@code persona}: un encargado <b>es</b> una persona con ese rol. No hay identificador propio.
 *
 * <p>Esa relación no se mapea como {@code @OneToOne} con {@code @MapsId} hacia la entidad de
 * personas, aunque JPA lo permita. Sería la única relación entre módulos del proyecto: las demás
 * entidades referencian por identificador, y traer aquí la entidad de otro contexto acotado ataría
 * la persistencia de clientes a la de personas. La identidad compartida la garantizan la llave
 * primaria y la clave foránea del esquema.
 *
 * <p>Las dos columnas de asignación son anulables porque solo una aplica según el tipo, y el
 * {@code CHECK} de la tabla obliga a que sea exactamente la que corresponde.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "encargado")
public class ManagerEntity {

    @Id
    @Column(name = "k_identificador", nullable = false)
    private UUID idPersona;

    @Column(name = "t_tipo_encargado", nullable = false, length = 16)
    private String tipo;

    @Column(name = "k_id_sede")
    private UUID idSede;

    @Column(name = "k_id_area_servicio")
    private UUID idAreaServicio;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
