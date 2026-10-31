import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/**
 * Los documentos que el servidor entrega en PDF: el informe de un reporte (2026-10-17) y la hoja de vida
 * de un equipo (2026-10-18). Lo que se pide igual se pide aquí, una vez.
 */

/** Un PDF y el nombre con que se descarga, que dice el servidor. */
export interface DocumentoEnPdf {
  readonly pdf: Blob;
  readonly nombre: string;
}

/** El nombre de {@code inline; filename="informe-1DC6F5.pdf"}; nulo si no lo trae. */
export function nombreDelArchivo(cabecera: string | null): string | null {
  return cabecera?.match(/filename="?([^";]+)"?/)?.[1] ?? null;
}

/** Un archivo cualquiera y el nombre con que se guarda: el ZIP de los informes de una orden (2026-10-31). */
export interface ArchivoDescargado {
  readonly archivo: Blob;
  readonly nombre: string;
}

/** Pide un PDF: ver {@link pedirArchivo}. */
export async function pedirPdf(
  http: HttpClient,
  url: string,
  nombrePorDefecto: string,
): Promise<DocumentoEnPdf> {
  const { archivo, nombre } = await pedirArchivo(http, url, nombrePorDefecto);

  return { pdf: archivo, nombre };
}

/**
 * Pide un binario. Llega como tal, así que un error también: el cuerpo del 404 o del 503 es un
 * {@code Blob} y no el JSON con su código. Se lee aquí y se vuelve a lanzar con el JSON, para que el
 * mensaje sea el del catálogo y no el genérico.
 */
export async function pedirArchivo(
  http: HttpClient,
  url: string,
  nombrePorDefecto: string,
): Promise<ArchivoDescargado> {
  try {
    const respuesta = await firstValueFrom(
      http.get(url, { observe: 'response', responseType: 'blob' }),
    );

    return {
      archivo: respuesta.body!,
      nombre: nombreDelArchivo(respuesta.headers.get('Content-Disposition')) ?? nombrePorDefecto,
    };
  } catch (fallo) {
    throw await conCuerpoLeido(fallo);
  }
}

/**
 * Guarda en el equipo un archivo ya descargado, con su nombre: un enlace temporal que se pulsa solo. La
 * dirección se libera después, cuando el navegador ya empezó a guardarlo.
 */
export function guardarArchivo({ archivo, nombre }: ArchivoDescargado): void {
  const direccion = URL.createObjectURL(archivo);
  const enlace = document.createElement('a');
  enlace.href = direccion;
  enlace.download = nombre;
  enlace.click();
  setTimeout(() => URL.revokeObjectURL(direccion));
}

/** Un error de una petición binaria con su cuerpo de error leído como JSON, si lo es. */
async function conCuerpoLeido(fallo: unknown): Promise<unknown> {
  if (!(fallo instanceof HttpErrorResponse) || !(fallo.error instanceof Blob)) {
    return fallo;
  }
  try {
    return new HttpErrorResponse({
      error: JSON.parse(await fallo.error.text()),
      headers: fallo.headers,
      status: fallo.status,
      statusText: fallo.statusText,
      url: fallo.url ?? undefined,
    });
  } catch {
    return fallo;
  }
}
