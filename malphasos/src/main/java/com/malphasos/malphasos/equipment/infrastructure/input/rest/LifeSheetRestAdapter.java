package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.client.infrastructure.input.security.ReadScopeResolver;
import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPdf;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetPdfServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.InterventionResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.LifeSheetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La hoja de vida de un equipo: un solo recurso con las cuatro secciones de RF-22.
 *
 * <p><b>Un recurso y no cinco, que es el cambio.</b> Antes estos datos existían pero repartidos: para
 * pintar una hoja de vida había que pedir el equipo, su modelo, su equipo de catálogo, su tipo, su
 * marca, su fabricante, su área, su sede y su cliente, y cruzarlos en el cliente —ninguna respuesta
 * del módulo trae nombres—. Eso está anotado como deuda desde el 2026-09-26 y aquí deja de pagarse
 * para este documento.
 *
 * <p><b>Solo lectura</b>, que es la decisión tomada sobre RF-24: cada dato se corrige donde vive, con
 * las operaciones que ya existen. Ver {@code LifeSheetServicePort}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api")
@Tag(name = "LifeSheet", description = "Hoja de vida de un equipo instalado")
public class LifeSheetRestAdapter {

    private final LifeSheetServicePort lifeSheetServicePort;
    /** La hoja de vida en PDF, del servidor desde el 2026-10-18. */
    private final LifeSheetPdfServicePort lifeSheetPdfServicePort;

    /** Traduce quién llama a un alcance de lectura. Ver {@code ClientRestAdapter}. */
    private final ReadScopeResolver readScopeResolver;
    /** El membrete: desde el 2026-10-17 sale de la base, donde lo edita un administrador. */
    private final ServiceCompanyServicePort serviceCompanyServicePort;
    private final EquipmentRestMapper restMapper;

    @Operation(
            summary = "Hoja de vida de un equipo",
            description = "Identificacion, tecnica, fabricante e historial de intervenciones.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/client-equipments/{id}/life-sheet")
    public LifeSheetResponse getLifeSheet(
            @Parameter(description = "Identificador del equipo instalado") @PathVariable UUID id,
            Authentication autenticacion) {

        return toResponse(lifeSheetServicePort.findByEquipment(id, readScopeResolver.scopeFor(autenticacion)));
    }

    @Operation(
            summary = "La hoja de vida en PDF",
            description = "La misma hoja de vida, compuesta en el servidor y convertida por Gotenberg, con "
                    + "el número de página en cada una. Con el mismo alcance que la hoja de vida.")
    @ApiResponse(responseCode = "200", description = "El PDF",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping(value = "/client-equipments/{id}/life-sheet/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getLifeSheetPdf(
            @Parameter(description = "Identificador del equipo instalado") @PathVariable UUID id,
            Authentication autenticacion) {

        LifeSheetPdf pdf = lifeSheetPdfServicePort.pdf(id, readScopeResolver.scopeFor(autenticacion));

        // En línea y no como adjunto: la pantalla lo muestra, y su botón lo descarga con este nombre.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.nombre()).build().toString())
                .body(pdf.contenido());
    }

    /*
     * El membrete se pone aqui y no en LifeSheetService: no es un dato del equipo sino del documento
     * que se entrega, y la capa de aplicacion no tiene por que saber de que empresa es.
     */
    private LifeSheetResponse toResponse(LifeSheet hoja) {
        return new LifeSheetResponse(
                new LifeSheetResponse.Identificacion(
                        hoja.identificacion().idEquipoCliente(),
                        hoja.identificacion().numeroHojaVida(),
                        hoja.identificacion().serie(),
                        hoja.identificacion().numeroInventario(),
                        hoja.identificacion().codigoInterno(),
                        hoja.identificacion().proveedor(),
                        hoja.identificacion().fechaCompra(),
                        hoja.identificacion().valorCompra(),
                        hoja.identificacion().cliente(),
                        hoja.identificacion().documentoCliente(),
                        hoja.identificacion().sede(),
                        hoja.identificacion().direccionSede(),
                        hoja.identificacion().ciudadSede(),
                        hoja.identificacion().areaServicio(),
                        hoja.identificacion().estadoActivo(),
                        hoja.identificacion().responsables(),
                        hoja.identificacion().telefonosCliente(),
                        hoja.identificacion().correosCliente()),
                new LifeSheetResponse.Tecnica(
                        hoja.tecnica().tipoEquipo(),
                        hoja.tecnica().definicionTecnica(),
                        hoja.tecnica().tecnologiaPredominante(),
                        hoja.tecnica().recomendacionesCuidado(),
                        hoja.tecnica().uso(),
                        hoja.tecnica().limpiezaCotidiana(),
                        hoja.tecnica().marca(),
                        hoja.tecnica().modelo(),
                        hoja.tecnica().registroInvima(),
                        restMapper.toResponse(hoja.tecnica().fichaTecnica()),
                        hoja.tecnica().protocoloPreventivo().stream()
                                .map(paso -> new LifeSheetResponse.PasoDelProtocolo(
                                        paso.prioridad(), paso.nombre(), paso.descripcion(), paso.indicacion()))
                                .toList()),
                new LifeSheetResponse.Fabricante(
                        hoja.fabricante().nombre(), hoja.fabricante().pais()),
                hoja.servicioTecnico().stream().map(LifeSheetRestAdapter::toResponse).toList(),
                serviceCompanyServicePort.find().map(LifeSheetRestAdapter::toResponse).orElse(null));
    }

    /** Sin empresa configurada el membrete viaja nulo, y el documento sale sin él. */
    private static LifeSheetResponse.Empresa toResponse(ServiceCompany empresa) {
        CompanyDetails datos = empresa.getDatos();

        return new LifeSheetResponse.Empresa(
                datos.nombre(),
                datos.nit(),
                datos.direccion(),
                datos.ciudad(),
                datos.telefonos(),
                datos.movil(),
                datos.correo(),
                empresa.logo().map(CompanyLogo::dataUri).orElse(null),
                datos.garantia(),
                datos.aclaracion(),
                datos.codigoHojaDeVida(),
                datos.codigoInforme());
    }

    private static InterventionResponse toResponse(Intervention intervencion) {
        return new InterventionResponse(
                intervencion.id(),
                intervencion.idEquipoCliente(),
                intervencion.idReporteServicio(),
                intervencion.fechaServicio(),
                intervencion.tipoServicio().name(),
                intervencion.resultado().name(),
                intervencion.descripcion(),
                intervencion.responsable());
    }
}
