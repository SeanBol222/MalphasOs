package com.malphasos.malphasos.equipment.application.services.lifeSheet;

import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.HistoryLine;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Escribe la hoja de vida como sale en el papel (2026-10-18). Son las reglas que tenía la pantalla cuando
 * la hoja se imprimía desde el navegador —{@code hoja-de-vida.ts}—, pasadas aquí cuando el PDF lo empezó a
 * generar el servidor: la raya donde falta un dato, «Sin servicios» para un equipo sin historial, el
 * responsable con la empresa, el valor en pesos sin decimales.
 */
public final class LifeSheetPrintComposer {

    /** Lo que dice la hoja cuando un dato no está registrado. */
    static final String SIN_DATO = "—";

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd MMM yyyy", COLOMBIA);

    private LifeSheetPrintComposer() {
    }

    /**
     * @param ciudadDeLaEmpresa el nombre de su ciudad, que la empresa guarda por su identificador (V44);
     *     nulo si no tiene
     * @param hoy la fecha de actualización, que es la de impresión: decidido el 2026-10-05
     */
    public static LifeSheetPrint compose(
            LifeSheet hoja, Optional<ServiceCompany> empresa, String ciudadDeLaEmpresa, LocalDate hoy) {
        LifeSheet.Identificacion id = hoja.identificacion();
        LifeSheet.Tecnica tecnica = hoja.tecnica();
        TechnicalSheet ficha = tecnica.fichaTecnica() == null ? TechnicalSheet.EMPTY : tecnica.fichaTecnica();
        LifeSheetPrint.Empresa membrete = empresa.map(e -> membrete(e, ciudadDeLaEmpresa))
                .orElse(LifeSheetPrint.Empresa.NINGUNA);
        String nombreDeLaEmpresa = empresa.map(e -> e.getDatos().nombre()).orElse(null);

        // Lo de los reportes y lo que hicieron otras empresas, junto y por fecha (V61).
        List<HistoryLine> lineas = hoja.historial();
        List<LifeSheetPrint.Linea> historial = lineas.stream()
                .map(linea -> linea(linea, nombreDeLaEmpresa))
                .toList();
        // El estado de hoy es el de la más reciente que dice cómo quedó: una externa puede no decirlo.
        String estadoActual = lineas.stream()
                .filter(linea -> linea.resultado() != null)
                .findFirst()
                .map(linea -> resultado(linea.resultado()))
                .orElse(historial.isEmpty() ? "Sin servicios" : SIN_DATO);

        return new LifeSheetPrint(
                id.idEquipoCliente(),
                membrete,
                oRaya(id.numeroHojaVida()),
                unidos(" ", tecnica.tipoEquipo(), tecnica.marca(), tecnica.modelo()),
                oRaya(id.serie()),
                oRaya(id.areaServicio()),
                oRaya(id.cliente()),
                !id.estadoActivo(),
                new LifeSheetPrint.Estado(
                        estadoActual,
                        historial.isEmpty() ? SIN_DATO : historial.getFirst().fecha(),
                        historial.size(),
                        ficha.riesgo() == null ? SIN_DATO : riesgo(ficha.riesgo())),
                new LifeSheetPrint.Identificacion(
                        oRaya(tecnica.tipoEquipo()),
                        oRaya(tecnica.marca()) + " · " + oRaya(tecnica.modelo()),
                        oRaya(id.serie()),
                        oRaya(id.numeroInventario()) + " · " + oRaya(id.codigoInterno()),
                        oRaya(tecnica.registroInvima()),
                        oRaya(id.areaServicio())),
                new LifeSheetPrint.Cliente(
                        oRaya(id.cliente()) + " · " + oRaya(id.documentoCliente()),
                        oRaya(id.sede()) + " · " + oRaya(id.ciudadSede()),
                        oRaya(id.direccionSede()),
                        lista(id.responsables()),
                        lista(id.telefonosCliente()),
                        lista(id.correosCliente())),
                oRaya(tecnica.definicionTecnica()),
                id.foto(),
                "Fabricante " + oRaya(hoja.fabricante().nombre()) + " · " + oRaya(hoja.fabricante().pais())
                        + " · Proveedor " + oRaya(id.proveedor())
                        + " · Compra " + fecha(id.fechaCompra())
                        + " · Valor " + pesos(id.valorCompra()),
                new LifeSheetPrint.Caracteristicas(
                        oRaya(tecnica.tecnologiaPredominante()),
                        oRaya(tecnica.uso()),
                        oRaya(ficha.alimentacion()),
                        conUnidad(ficha.frecuencia(), "Hz"),
                        ficha.voltaje() == null ? SIN_DATO + " V" : numero(ficha.voltaje()) + " V",
                        conUnidad(ficha.potencia(), "W"),
                        ficha.amperaje() == null ? SIN_DATO + " A" : numero(ficha.amperaje()) + " A",
                        oRaya(ficha.caracteristicas())),
                tecnica.protocoloPreventivo().stream()
                        .map(paso -> new LifeSheetPrint.Paso(paso.nombre(), paso.descripcion(), vacioANulo(paso.indicacion())))
                        .toList(),
                // Las recomendaciones son un solo texto en el tipo: cada línea es un consejo.
                tecnica.recomendacionesCuidado() == null
                        ? List.of()
                        : Arrays.stream(tecnica.recomendacionesCuidado().split("\n"))
                                .map(String::strip)
                                .filter(linea -> !linea.isEmpty())
                                .toList(),
                vacioANulo(tecnica.limpiezaCotidiana()),
                historial,
                unidos(" · ",
                        empresa.map(e -> e.getDatos().codigoHojaDeVida()).orElse(null),
                        "Actualizada el " + fecha(hoy)));
    }

    private static LifeSheetPrint.Empresa membrete(ServiceCompany empresa, String ciudad) {
        CompanyDetails datos = empresa.getDatos();
        List<String> telefonos = new ArrayList<>(datos.telefonos() == null ? List.of() : datos.telefonos());
        if (datos.movil() != null && !datos.movil().isBlank()) {
            telefonos.add("Móvil " + datos.movil());
        }

        return new LifeSheetPrint.Empresa(
                Objects.toString(datos.nombre(), ""),
                empresa.logo().map(CompanyLogo::dataUri).orElse(null),
                unidos(", ", datos.direccion(), ciudad),
                String.join(" · ", telefonos),
                Objects.toString(datos.correo(), ""));
    }

    private static LifeSheetPrint.Linea linea(HistoryLine linea, String empresa) {
        InterventionResult resultado = linea.resultado();

        return new LifeSheetPrint.Linea(
                fecha(linea.fecha()),
                servicio(linea.tipoServicio()),
                // Los procedimientos del reporte, congelados al cerrarse (V17), o lo que anotó quien metió la
                // externa; uno viejo sin ellos, la raya.
                oRaya(linea.descripcion()),
                linea.externa()
                        // «Externo · Metrología S.A.S. · CAL-2026-118»: quién la hizo y su certificado (V61).
                        ? unidos(" · ", "Externo", linea.empresaExterna(), linea.documento())
                        // «N.º 5C9C83 · Grace Hopper · Servicios de Ejemplo S.A.S.»: el reporte, el ingeniero y
                        // la empresa. El número, pedido por el usuario el 2026-11-23.
                        : unidos(" · ", "N.º " + linea.numeroReporte(), linea.responsable(), empresa),
                resultado == null ? "" : resultado.name(),
                resultado == null ? SIN_DATO : resultado(resultado),
                resultado != null && resultado != InterventionResult.OPERATIVO);
    }

    static String resultado(InterventionResult resultado) {
        return switch (resultado) {
            case OPERATIVO -> "Operativo";
            case OPERATIVO_CON_RESTRICCIONES -> "Operativo con restricciones";
            case FUERA_DE_SERVICIO -> "Fuera de servicio";
        };
    }

    static String servicio(InterventionType tipo) {
        return servicio(tipo.name());
    }

    /** Por su nombre, porque una externa admite además OTRO (V61). */
    static String servicio(String tipo) {
        return switch (tipo) {
            case "PREVENTIVO" -> "Preventivo";
            case "CORRECTIVO" -> "Correctivo";
            case "CALIBRACION" -> "Calibración";
            case "OTRO" -> "Otro";
            default -> tipo;
        };
    }

    static String riesgo(RiskClass clase) {
        return switch (clase) {
            case I -> "Clase I";
            case IIA -> "Clase IIa";
            case IIB -> "Clase IIb";
            case III -> "Clase III";
        };
    }

    /** «17 oct 2026», o la raya. Java ya abrevia sin punto en es-CO. */
    static String fecha(LocalDate fecha) {
        return fecha == null ? SIN_DATO : FECHA_CORTA.format(fecha);
    }

    /** El valor de compra en pesos y sin decimales, que es como se escribe en una hoja de vida. */
    static String pesos(Long valor) {
        if (valor == null) {
            return SIN_DATO;
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(COLOMBIA);
        // El valor es un entero: basta con no exigir los dos decimales que el peso trae por defecto.
        formato.setMinimumFractionDigits(0);

        return formato.format(valor);
    }

    /** «0,1»: como se escriben los decimales en Colombia, sin ceros de sobra. */
    static String numero(BigDecimal valor) {
        return new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(COLOMBIA)).format(valor);
    }

    private static String conUnidad(Integer valor, String unidad) {
        return (valor == null ? SIN_DATO : valor.toString()) + " " + unidad;
    }

    private static String oRaya(String texto) {
        return texto == null || texto.isBlank() ? SIN_DATO : texto;
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    /** Varios valores en una casilla se separan con «·»; ninguno es la raya. */
    private static String lista(List<String> valores) {
        return valores == null || valores.isEmpty() ? SIN_DATO : String.join(" · ", valores);
    }

    private static String unidos(String separador, String... partes) {
        return Stream.of(partes).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(separador));
    }
}
