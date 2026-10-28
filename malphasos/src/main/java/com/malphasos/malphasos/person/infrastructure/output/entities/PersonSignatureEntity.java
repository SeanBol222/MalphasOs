package com.malphasos.malphasos.person.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** La firma de una persona (V38): desde V45, la huella de su imagen en {@code imagen_documento}. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "firma_persona")
public class PersonSignatureEntity {

    @Id
    @Column(name = "k_identificador", nullable = false)
    private UUID idPersona;

    @Column(name = "k_huella", nullable = false)
    private String huella;

    @Column(name = "f_actualizada", nullable = false)
    private LocalDateTime actualizada;
}
