package com.malphasos.malphasos.equipment.application.model.lifeSheet;

import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La hoja de vida de un equipo instalado: el documento compilado que RF-22 describe.
 *
 * <p><b>No es una entidad y no tiene tabla.</b> Es una vista que se arma leyendo datos que ya viven
 * repartidos por la cadena del catálogo y por la organización del cliente, y <b>existe desde que el
 * equipo se registra</b>: un equipo recién dado de alta tiene su hoja de vida completa y su historial
 * en cero. No es que falte la hoja de vida; es que todavía no le han hecho nada.
 *
 * <p>Vive en {@code application/model} y no en {@code domain} por eso mismo: no tiene invariantes que
 * proteger ni comportamiento que ofrecer. Es el resultado de una consulta.
 *
 * <p><b>Las cuatro secciones son las cuatro que enumera el criterio de aceptación de RF-22</b>
 * —identificación, técnica, fabricante y servicio técnico—, en ese orden y con esos nombres, para que
 * el documento y el requisito se puedan leer uno contra el otro sin traducir nada.
 *
 * <p>La cuarta es la única que sale de los reportes, y la única que tiene tabla propia.
 */
public record LifeSheet(
        Identificacion identificacion,
        Tecnica tecnica,
        Fabricante fabricante,
        List<Intervention> servicioTecnico) {

    /** Qué unidad es y de quién, que es lo que la distingue de otra igual. */
    public record Identificacion(
            UUID idEquipoCliente,
            String serie,
            String numeroInventario,
            LocalDate fechaCompra,
            Long valorCompra,
            String cliente,
            String documentoCliente,
            String sede,
            String direccionSede,
            String ciudadSede,
            String areaServicio,
            boolean estadoActivo) {
    }

    /** Qué es el equipo: su tipo, su marca y su modelo, con lo que el tipo declara. */
    public record Tecnica(
            String tipoEquipo,
            String definicionTecnica,
            String tecnologiaPredominante,
            String recomendacionesCuidado,
            Integer voltaje,
            java.math.BigDecimal amperaje,
            String marca,
            String modelo,
            String registroInvima) {
    }

    /** Quién lo fabricó y dónde. */
    public record Fabricante(String nombre, String pais) {
    }
}
