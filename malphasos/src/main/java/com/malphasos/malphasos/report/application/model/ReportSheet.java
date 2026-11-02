package com.malphasos.malphasos.report.application.model;

import java.util.List;
import java.util.UUID;

/**
 * El informe de un reporte listo para imprimirse (2026-10-17): cada dato ya dicho como lo dice el papel.
 * Lo compone {@code ReportSheetComposer} desde el informe congelado y el reporte, y lo pinta la plantilla
 * del PDF. Hasta ese día lo calculaba el navegador; con el PDF en el servidor, el diseño vive en un solo
 * sitio.
 *
 * <p>Las imágenes —el logo y las dos firmas— van como data: URI, y son nulas si no las hay. El QR no va
 * dibujado: lleva el reporte, y quien pinta sabe en qué dirección se publica la aplicación.
 *
 * @param paginas 1, o 2 si el reporte tiene lecturas: la página 2 es la verificación metrológica
 */
public record ReportSheet(
        boolean borrador,
        String numero,
        String numeroOrden,
        String empresa,
        String logo,
        String lineaDelEquipo,
        UUID idReporte,
        Estado estado,
        Cliente cliente,
        Equipo equipo,
        List<Casilla> casillas,
        List<FilaDeIngreso> ingreso,
        List<Paso> pasos,
        List<Parte> relato,
        String aclaracion,
        String observaciones,
        String garantia,
        Firmas firmas,
        String pie,
        int paginas,
        List<Patron> trazabilidad,
        List<Medida> medidas,
        String hojaDeVida) {

    public ReportSheet {
        casillas = List.copyOf(casillas);
        ingreso = List.copyOf(ingreso);
        pasos = List.copyOf(pasos);
        relato = List.copyOf(relato);
        trazabilidad = List.copyOf(trazabilidad);
        medidas = List.copyOf(medidas);
    }

    /** La fila de estado: lo que se mira primero. */
    public record Estado(String resultado, String fechaDelServicio, String proximoServicio, String verificacion) {
    }

    public record Cliente(
            String razonSocialNit, String sedeCiudad, String direccion, String supervisor, String telefono,
            String correo) {
    }

    public record Equipo(
            String marcaModelo, String placaCodigo, String area, String tecnologiaUso, String riesgo,
            String alimentacion) {
    }

    public record Casilla(String texto, boolean marcada) {
    }

    /** Una verificación al ingreso o un accesorio. */
    public record FilaDeIngreso(String texto, String estado, String observacion, boolean accesorio) {
    }

    /** Un paso del protocolo: «✓», «N/A» con su nota, o en blanco si quedó pendiente. */
    public record Paso(String marca, String nombre, String nota) {
    }

    /** Lo escrito a mano: falla, diagnóstico, causa, procedimientos y repuestos, solo lo que tiene texto. */
    public record Parte(String titulo, String texto) {
    }

    /**
     * @param supervisor quien aprobó la orden (V54) o, mientras no la apruebe nadie, quienes la supervisan
     * @param detalleSupervisor «Supervisor responsable», y con la aprobación su cargo y el día
     * @param firmaSupervisor la firma de quien aprobó como data: URI, o nula
     */
    public record Firmas(
            String ingeniero, String firmaIngeniero, String supervisor, String recibe, String cargoRecibe,
            String firmaRecibe, String detalleSupervisor, String firmaSupervisor) {

        /** Sin aprobación: la línea del supervisor queda para firmar a mano. */
        public Firmas(
                String ingeniero, String firmaIngeniero, String supervisor, String recibe, String cargoRecibe,
                String firmaRecibe) {
            this(ingeniero, firmaIngeniero, supervisor, recibe, cargoRecibe, firmaRecibe,
                    "Supervisor responsable", null);
        }
    }

    /** Un patrón usado, una vez aunque se usara en dos verificaciones. */
    public record Patron(
            String instrumento, String detalle, String serie, String codigo, String rango, String resolucion,
            String certificado, String calibrado, String vence, String calibrador) {
    }

    /** Una verificación medida: su tabla, su gráfica y contra qué patrones. */
    public record Medida(
            String magnitud, String unidad, String tolerancia, List<Fila> filas, String resumen, String patrones,
            Grafica grafica, String leyenda) {

        public Medida {
            filas = List.copyOf(filas);
        }
    }

    public record Fila(String registro, String patron, String equipo, String error, String conforme, boolean fuera) {
    }

    /**
     * La gráfica de error, ya calculada en unidades del lienzo de {@code ancho} × {@code alto}: la plantilla
     * solo pinta.
     *
     * @param banda nula si no hay tolerancia que dibujar
     */
    public record Grafica(Banda banda, double ceroY, List<Punto> puntos, List<Marca> marcas) {

        public static final int ANCHO = 360;
        public static final int ALTO = 140;
        public static final int IZQUIERDA = 48;
        public static final int DERECHA = 352;
        public static final int ARRIBA = 10;
        public static final int ABAJO = 130;

        public Grafica {
            puntos = List.copyOf(puntos);
            marcas = List.copyOf(marcas);
        }
    }

    public record Banda(double y, double alto) {
    }

    /** @param conforme nulo si la lectura no tiene tolerancia: no se sabe, y se pinta neutro */
    public record Punto(double x, double y, Boolean conforme) {

        public boolean fuera() {
            return Boolean.FALSE.equals(conforme);
        }
    }

    public record Marca(double y, String texto) {
    }
}
