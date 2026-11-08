import { InjectionToken } from '@angular/core';

/**
 * Reducir una foto en el navegador antes de subirla (2026-11-08), para que ocupe lo menos posible.
 *
 * <p>Decidido por el usuario: la foto de un equipo se guarda <b>solo en PNG</b>, y que sea pequeña la hace
 * la resolución. El PNG no pierde calidad, así que con una foto pesa mucho por píxel: a 400 px de lado
 * mayor ocupa unos 60 a 180 KB, medidos sobre fotos reales, y el recuadro de la hoja de vida mide 132 px,
 * de modo que 400 bastan para imprimirlo nítido. Una foto de teléfono, de 3 a 5 MB, no sale del equipo.
 */

/** El lado mayor de la foto guardada, en píxeles. */
export const LADO_MAYOR = 400;

/** Lo que admite el servidor (V58); con 400 px no se acerca. */
export const TAMANO_MAXIMO = 512 * 1024;

/** Las medidas de la foto reducida: el lado mayor a {@link LADO_MAYOR}, sin agrandar ni deformar. */
export function medidasReducidas(
  ancho: number,
  alto: number,
  lado = LADO_MAYOR,
): { ancho: number; alto: number } {
  const escala = Math.min(1, lado / Math.max(ancho, alto));

  return {
    ancho: Math.max(1, Math.round(ancho * escala)),
    alto: Math.max(1, Math.round(alto * escala)),
  };
}

/**
 * La foto reducida, en PNG. Lee la orientación de la cámara —una foto de teléfono en vertical no sale
 * acostada— y si aun así pasara del máximo prueba con un lado menor.
 */
export async function reducirFoto(archivo: Blob, lado = LADO_MAYOR): Promise<Blob> {
  const imagen = await createImageBitmap(archivo, { imageOrientation: 'from-image' });
  try {
    const { ancho, alto } = medidasReducidas(imagen.width, imagen.height, lado);
    const lienzo = document.createElement('canvas');
    lienzo.width = ancho;
    lienzo.height = alto;
    const contexto = lienzo.getContext('2d');
    if (!contexto) {
      throw new Error('Este navegador no puede reducir la foto');
    }
    contexto.imageSmoothingQuality = 'high';
    contexto.drawImage(imagen, 0, 0, ancho, alto);

    const png = await new Promise<Blob>((resolver, fallar) =>
      lienzo.toBlob(
        (blob) => (blob ? resolver(blob) : fallar(new Error('No se pudo reducir la foto'))),
        'image/png',
      ),
    );

    return png.size > TAMANO_MAXIMO && lado > 100
      ? reducirFoto(archivo, Math.round(lado * 0.75))
      : png;
  } finally {
    imagen.close();
  }
}

/** La reducción, inyectable: el entorno de pruebas no tiene lienzo, y las pruebas la sustituyen. */
export const REDUCIR_FOTO = new InjectionToken<(archivo: Blob) => Promise<Blob>>('REDUCIR_FOTO', {
  providedIn: 'root',
  factory: () => reducirFoto,
});
