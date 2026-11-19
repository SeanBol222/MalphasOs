package com.malphasos.malphasos.report.infrastructure.output.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "t_resultado")
    private String resultado;

    /** Cuándo se cerró. Nulo mientras el reporte está en borrador. */
    @Column(name = "f_finalizado")
    private LocalDateTime finalizado;

    @Column(name = "b_estado_activo", nullable = false)
    private boolean estadoActivo;

    /** El informe congelado al cerrar, en JSON (V37). Lo escribe y lo lee el mapper. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "t_documento")
    private String documento;

    @Column(name = "b_documento_tardio", nullable = false)
    private boolean documentoTardio;

    /** La firma de quien recibe, por su huella en imagen_documento, y cuándo se firmó (V39). */
    @Column(name = "k_huella_firma_recibe")
    private String huellaFirmaRecibe;

    @Column(name = "f_firma_recibe")
    private LocalDateTime firmaRecibe;

    /** Quien recibió, si es una persona registrada (V55). */
    @Column(name = "k_id_recibe")
    private UUID idRecibe;

    /**
     * Lo que el reporte dice, en JSON (V59): textos, ingreso, quien recibe sin cuenta, pasos, accesorios y,
     * por verificación, sus patrones y sus lecturas. Lo escribe y lo lee el mapper.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "t_contenido", nullable = false)
    private String contenido;
}
