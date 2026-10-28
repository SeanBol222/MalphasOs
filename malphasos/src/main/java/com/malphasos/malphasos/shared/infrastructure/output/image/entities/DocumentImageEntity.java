package com.malphasos.malphasos.shared.infrastructure.output.image.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una versión de una imagen que algún informe congeló: el logo (V37) o la firma del ingeniero (V38). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "imagen_documento")
public class DocumentImageEntity {

    @Id
    @Column(name = "k_huella", nullable = false)
    private String huella;

    @Column(name = "b_contenido", nullable = false)
    private byte[] contenido;

    @Column(name = "t_tipo", nullable = false)
    private String tipo;
}
