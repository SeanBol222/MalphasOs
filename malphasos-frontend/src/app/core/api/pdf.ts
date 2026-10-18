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

/**
 * Pide un PDF. Llega como binario, así que un error también: el cuerpo del 404 o del 503 es un
 * {@code Blob} y no el JSON con su código. Se lee aquí y se vuelve a lanzar con el JSON, para que el
 * mensaje sea el del catálogo y no el genérico.
 */
export async function pedirPdf(
  http: HttpClient,
  url: string,
  nombrePorDefecto: string,
): Promise<DocumentoEnPdf> {
  try {
    const respuesta = await firstValueFrom(
      http.get(url, { observe: 'response', responseType: 'blob' }),
    );

    return {
      pdf: respuesta.body!,
      nombre: nombreDelArchivo(respuesta.headers.get('Content-Disposition')) ?? nombrePorDefecto,
    };
  } catch (fallo) {
    throw await conCuerpoLeido(fallo);
  }
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
