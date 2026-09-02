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
 * Representación JPA de una sede.
 *
 * <p>El cliente y la ciudad se guardan como identificadores y no como relaciones: son agregados
 * aparte, y mapearlos obligaría a cargar el cliente entero cada vez que se lee una sede.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sede")
public class HeadquarterEntity {

    @Id
    @Column(name = "k_id_sede", nullable = false)
    private UUID id;

    @Column(name = "n_nombre_sede", nullable = false)
    private String nombre;

    @Column(name = "t_calle", nullable = false)
    private String calle;

    @Column(name = "t_carrera", nullable = false)
    private String carrera;

    @Column(name = "t_numero", nullable = false)
    private String numero;

    @Column(name = "k_id_cliente", nullable = false)
    private UUID idCliente;

    @Column(name = "k_id_ciudad", nullable = false)
    private UUID idCiudad;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
