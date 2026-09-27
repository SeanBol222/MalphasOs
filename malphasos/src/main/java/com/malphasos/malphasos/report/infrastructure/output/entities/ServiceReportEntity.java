package com.malphasos.malphasos.report.infrastructure.output.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representación JPA de un reporte de servicio.
 *
 * <p>La orden y el equipo se guardan como identificadores: pertenecen a otros módulos y mapearlos
 * como relación ataría estas tablas entre sí. Aquí eso tiene un efecto que conviene decir en voz
 * alta: <b>la clave foránea compuesta contra el puente de la orden existe en la base y no en el
 * mapeo</b>. Es la base la que impide reportar un equipo ajeno a la orden; JPA solo escribe las dos
 * columnas.
 *
 * <p>El estado y el resultado se guardan por su nombre y no por su posición: guardar el ordinal haría
 * que reordenar el enum reinterpretara en silencio las filas ya escritas, y además los {@code CHECK}
 * del esquema esperan el texto.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "reporte_servicio")
public class ServiceReportEntity {

    @Id
    @Column(name = "k_id_reporte_servicio", nullable = false)
    private UUID id;

    @Column(name = "k_id_orden_trabajo", nullable = false)
    private UUID idOrdenTrabajo;

    @Column(name = "k_id_equipo_cliente", nullable = false)
    private UUID idEquipoCliente;

    @Column(name = "t_estado_reporte", nullable = false)
    private String estado;

    @Column(name = "t_falla_reportada")
    private String fallaReportada;

    @Column(name = "t_diagnostico")
    private String diagnostico;

    @Column(name = "t_procedimientos")
    private String procedimientos;

    @Column(name = "t_observaciones")
    private String observaciones;

    @Column(name = "t_resultado")
    private String resultado;

    /** Cuándo se cerró. Nulo mientras el reporte está en borrador. */
    @Column(name = "t_finalizado")
    private LocalDateTime finalizado;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    @OneToMany(mappedBy = "reporte", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VerificationReadingEntity> lecturas = new ArrayList<>();
}
