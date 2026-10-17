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

/** La firma de una persona (V38). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "firma_persona")
public class PersonSignatureEntity {

    @Id
    @Column(name = "k_id_persona", nullable = false)
    private UUID idPersona;

    @Column(name = "b_imagen", nullable = false)
    private byte[] imagen;

    @Column(name = "f_actualizada", nullable = false)
    private LocalDateTime actualizada;
}
