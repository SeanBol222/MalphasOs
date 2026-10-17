package com.malphasos.malphasos.report.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una versión del logo que algún informe congeló (V37). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "logo_documento")
public class DocumentLogoEntity {

    @Id
    @Column(name = "k_huella", nullable = false)
    private String huella;

    @Column(name = "b_contenido", nullable = false)
    private byte[] contenido;

    @Column(name = "t_tipo", nullable = false)
    private String tipo;
}
