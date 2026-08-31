package com.malphasos.malphasos.location.infrastructure.output.entities;

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
 * Representación JPA de una ciudad.
 *
 * <p>El país se guarda como su identificador y no como una relación {@code @ManyToOne}. El agregado
 * de dominio conoce el identificador del país, no un objeto {@code Country}: mapear la relación
 * obligaría a cargar el país entero cada vez que se lee una ciudad, y a decidir qué hacer con esa
 * instancia al reconstruir el agregado.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ciudad")
public class CityEntity {

    @Id
    @Column(name = "k_id_ciudad", nullable = false)
    private UUID id;

    @Column(name = "n_nombre_ciudad", nullable = false)
    private String nombre;

    @Column(name = "k_id_pais", nullable = false)
    private UUID idPais;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;
}
