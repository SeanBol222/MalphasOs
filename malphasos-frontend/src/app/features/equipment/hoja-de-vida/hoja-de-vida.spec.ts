import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { Component, input, output } from '@angular/core';
import { EncuadreDeFoto } from '../../../shared/foto/encuadre-de-foto';
import { REDUCIR_FOTO } from '../../../shared/foto/reducir-foto';
import { HojaDeVida } from './hoja-de-vida';

const ID = 'e1';
const URL_PDF = `http://localhost:8081/v1/api/client-equipments/${ID}/life-sheet/pdf`;
const PDF = new Blob(['%PDF-1.7'], { type: 'application/pdf' });

/**
 * La hoja de vida como el PDF del servidor (2026-10-18). Lo que el PDF imprime lo fijan las pruebas del
 * backend —{@code LifeSheetHtmlRendererTest} y {@code LifeSheetPrintComposerTest}—, que heredaron los casos
 * de esta cuando la hoja la pintaba Angular y se imprimía desde el navegador. Aquí, que se pida, se
 * muestre, se exporte y que sus errores se expliquen.
 */
describe('Hoja de vida', () => {
  let fixture: ComponentFixture<HojaDeVida>;
  let http: HttpTestingController;
  let creadas: string[];
  let liberadas: string[];
  const { createObjectURL, revokeObjectURL } = URL;

  beforeEach(() => {
    // jsdom no implementa las direcciones blob:.
    creadas = [];
    liberadas = [];
    let siguiente = 0;
    URL.createObjectURL = () => {
      const direccion = `blob:prueba/${++siguiente}`;
      creadas.push(direccion);
      return direccion;
    };
    URL.revokeObjectURL = (direccion: string) => liberadas.push(direccion);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read'] }),
      ],
    });
    fixture = TestBed.createComponent(HojaDeVida);
    fixture.componentRef.setInput('id', ID);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // En finally: si verify() lanza, el doble no se queda puesto para el archivo siguiente.
    try {
      http.verify();
    } finally {
      URL.createObjectURL = createObjectURL;
      URL.revokeObjectURL = revokeObjectURL;
    }
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => (raiz().textContent ?? '').replace(/\s+/g, ' ').trim();

  async function abrir(
    responder: (peticion: ReturnType<HttpTestingController['expectOne']>) => void = (peticion) =>
      peticion.flush(PDF, {
        headers: { 'Content-Disposition': 'inline; filename="hoja-de-vida-HV-CDN-0001.pdf"' },
      }),
  ): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
    responder(http.expectOne(URL_PDF));
    await asentar(fixture);
  }

  it('muestra el PDF del servidor en el visor, sin pasar por la impresión del navegador', async () => {
    await abrir();

    expect(raiz().querySelector('iframe[data-visor]')!.getAttribute('src')).toBe('blob:prueba/1');
    expect(texto()).not.toContain('Imprimir');
  });

  it('«Exportar PDF» lo descarga con el nombre que da el servidor', async () => {
    await abrir();
    const exportar = raiz().querySelector('[data-exportar]')!;

    expect(exportar.textContent?.trim()).toBe('Exportar PDF');
    expect(exportar.getAttribute('href')).toBe('blob:prueba/1');
    expect(exportar.getAttribute('download')).toBe('hoja-de-vida-HV-CDN-0001.pdf');
  });

  it('sin nombre en la respuesta, se descarga con uno que dice qué es', async () => {
    await abrir((peticion) => peticion.flush(PDF));

    expect(raiz().querySelector('[data-exportar]')!.getAttribute('download')).toBe(
      'hoja-de-vida.pdf',
    );
  });

  it('mientras llega, lo dice; y no ofrece exportar lo que no hay', async () => {
    fixture.detectChanges();
    await asentar(fixture);

    expect(texto()).toContain('Preparando el PDF…');
    expect(raiz().querySelector('[data-exportar]')).toBeNull();

    http.expectOne(URL_PDF).flush(PDF);
    await asentar(fixture);
  });

  it('enlaza los reportes del equipo y la vuelta al listado', async () => {
    await abrir();

    expect(raiz().querySelector(`a[href="/equipos/${ID}/historial"]`)?.textContent?.trim()).toBe(
      'Ver los reportes',
    );
    expect(raiz().querySelector('a[href="/equipos"]')).not.toBeNull();
  });

  it('si el conversor no responde, lo dice con el mensaje del catálogo, leído del cuerpo binario', async () => {
    // Una petición binaria recibe el error también como Blob: sin leerlo, el mensaje sería el genérico.
    await abrir((peticion) =>
      peticion.flush(
        new Blob([JSON.stringify({ code: 'ERR_EQUIPMENT_020' })], { type: 'application/json' }),
        {
          status: 503,
          statusText: 'Service Unavailable',
        },
      ),
    );

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
      'No se pudo generar el PDF de la hoja de vida',
    );
    expect(raiz().querySelector('iframe')).toBeNull();
    expect(raiz().querySelector('[data-exportar]')).toBeNull();
  });

  it('un equipo que no existe, o que no se ve, lo dice', async () => {
    await abrir((peticion) =>
      peticion.flush(
        new Blob([JSON.stringify({ code: 'ERR_EQUIPMENT_006' })], { type: 'application/json' }),
        {
          status: 404,
          statusText: 'Not Found',
        },
      ),
    );

    expect(raiz().querySelector('[role="alert"]')).not.toBeNull();
    expect(raiz().querySelector('iframe')).toBeNull();
  });

  it('al salir de la pantalla libera la memoria del PDF', async () => {
    await abrir();

    fixture.destroy();

    expect(liberadas).toEqual(['blob:prueba/1']);
  });
});

/** Lo que el recorte devuelve en las pruebas: el encuadre real necesita un lienzo, que jsdom no tiene. */
const RECORTE = new Blob(['recorte cuadrado'], { type: 'image/png' });

/** El encuadre, sustituido: dice qué foto le llegó y guarda, cancela o falla a voluntad. */
@Component({
  selector: 'app-encuadre-de-foto',
  template: `
    <div data-encuadre [attr.data-archivo]="archivo().name">
      <button type="button" (click)="listo.emit(recorte)">Guardar encuadre</button>
      <button type="button" (click)="cancelado.emit()">Cancelar encuadre</button>
      <button type="button" (click)="fallo.emit()">Fallar encuadre</button>
    </div>
  `,
})
class EncuadreFalso {
  readonly archivo = input.required<File>();
  readonly listo = output<Blob>();
  readonly cancelado = output<void>();
  readonly fallo = output<void>();
  protected readonly recorte = RECORTE;
}

/** La foto del equipo (V58, 2026-11-08): se pone, se cambia y se quita desde la hoja de vida. */
describe('Hoja de vida: la foto del equipo', () => {
  const URL_HOJA = `http://localhost:8081/v1/api/client-equipments/${ID}/life-sheet`;
  const URL_FOTO = `http://localhost:8081/v1/api/client-equipments/${ID}/photo`;
  const FOTO = 'data:image/png;base64,iVBORw0KGgo=';
  const REDUCIDA = new Blob(['png reducido'], { type: 'image/png' });

  let fixture: ComponentFixture<HojaDeVida>;
  let http: HttpTestingController;
  let reducidas: Blob[];
  let reducir: (archivo: Blob) => Promise<Blob>;
  const { createObjectURL, revokeObjectURL } = URL;

  function montar(autoridades: readonly string[] = ['equipment.read', 'equipment.write']): void {
    URL.createObjectURL = () => 'blob:prueba/1';
    URL.revokeObjectURL = () => undefined;
    reducidas = [];
    reducir = async (archivo) => {
      reducidas.push(archivo);
      return REDUCIDA;
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        { provide: REDUCIR_FOTO, useValue: (archivo: Blob) => reducir(archivo) },
      ],
    });
    TestBed.overrideComponent(HojaDeVida, {
      remove: { imports: [EncuadreDeFoto] },
      add: { imports: [EncuadreFalso] },
    });
    fixture = TestBed.createComponent(HojaDeVida);
    fixture.componentRef.setInput('id', ID);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    try {
      http.verify();
    } finally {
      URL.createObjectURL = createObjectURL;
      URL.revokeObjectURL = revokeObjectURL;
    }
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const bloque = () => raiz().querySelector<HTMLElement>('[data-foto]');
  const boton = (texto: string) =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === texto);

  /** La hoja de vida con la foto que diga, y su PDF. */
  async function responderHoja(foto: string | null): Promise<void> {
    await asentar(fixture);
    for (const peticion of http.match(URL_HOJA)) {
      peticion.flush({ identificacion: { foto } });
    }
    for (const peticion of http.match(URL_PDF)) {
      peticion.flush(PDF);
    }
    await asentar(fixture);
  }

  async function abrir(foto: string | null = null): Promise<void> {
    fixture.detectChanges();
    await responderHoja(foto);
  }

  const encuadre = () => raiz().querySelector<HTMLElement>('[data-encuadre]');

  /** Guarda el encuadre abierto: lo que se sube es su recorte. */
  async function guardarEncuadre(): Promise<void> {
    boton('Guardar encuadre')!.click();
    await asentar(fixture);
  }

  async function elegir(archivo: File): Promise<void> {
    const entrada = raiz().querySelector<HTMLInputElement>('#hv-foto-archivo')!;
    Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));
    await asentar(fixture);
  }

  it('quien solo lee no ve el bloque ni pide la hoja de vida: la foto le llega en el PDF', async () => {
    montar(['equipment.read']);
    fixture.detectChanges();
    await asentar(fixture);
    http.expectOne(URL_PDF).flush(PDF);
    await asentar(fixture);

    expect(bloque()).toBeNull();
    http.expectNone(URL_HOJA);
  });

  it('sin foto lo dice y ofrece subirla, con la cámara en un teléfono', async () => {
    montar();
    await abrir();

    expect(bloque()!.querySelector('[data-sin-foto]')).not.toBeNull();
    expect(bloque()!.querySelector('label')!.textContent?.trim()).toBe('Subir foto');
    const entrada = raiz().querySelector<HTMLInputElement>('#hv-foto-archivo')!;
    expect(entrada.getAttribute('accept')).toBe('image/*');
    expect(entrada.getAttribute('capture')).toBe('environment');
    expect(boton('Quitar')).toBeUndefined();
  });

  it('con foto la muestra y ofrece cambiarla o quitarla', async () => {
    montar();
    await abrir(FOTO);

    expect(bloque()!.querySelector('img')!.getAttribute('src')).toBe(FOTO);
    expect(bloque()!.querySelector('label')!.textContent?.trim()).toBe('Cambiar foto');
    expect(boton('Quitar')).toBeDefined();
  });

  it('la foto elegida se encuadra primero, y no se sube nada hasta guardar el encuadre', async () => {
    montar();
    await abrir();

    await elegir(new File(['foto de 4 MB'], 'IMG_0001.jpg', { type: 'image/jpeg' }));

    expect(encuadre()!.dataset['archivo']).toBe('IMG_0001.jpg');
    http.expectNone({ method: 'PATCH', url: URL_FOTO });
    expect(reducidas).toEqual([]);
    expect(raiz().querySelector<HTMLInputElement>('#hv-foto-archivo')!.disabled).toBe(true);
  });

  it('cancelar el encuadre no sube nada y deja la foto como estaba', async () => {
    montar();
    await abrir(FOTO);

    await elegir(new File(['otra'], 'otra.jpg', { type: 'image/jpeg' }));
    boton('Cancelar encuadre')!.click();
    await asentar(fixture);

    expect(encuadre()).toBeNull();
    expect(reducidas).toEqual([]);
    http.expectNone({ method: 'PATCH', url: URL_FOTO });
    expect(bloque()!.querySelector('img')!.getAttribute('src')).toBe(FOTO);
  });

  it('si el encuadre no puede leer la imagen, lo dice y no sube nada', async () => {
    montar();
    await abrir();

    await elegir(new File(['dañada'], 'foto.jpg', { type: 'image/jpeg' }));
    boton('Fallar encuadre')!.click();
    await asentar(fixture);

    expect(encuadre()).toBeNull();
    expect(bloque()!.querySelector('[role="alert"]')!.textContent?.trim()).toBe(
      'No se pudo leer la foto. Elija una imagen.',
    );
    http.expectNone({ method: 'PATCH', url: URL_FOTO });
  });

  it('al guardar el encuadre se reduce su recorte y se sube, no la foto original', async () => {
    montar();
    await abrir();
    const original = new File(['foto de 4 MB'], 'IMG_0001.jpg', { type: 'image/jpeg' });

    await elegir(original);
    await guardarEncuadre();

    expect(encuadre()).toBeNull();
    expect(reducidas).toEqual([RECORTE]);
    const subida = http.expectOne({ method: 'PATCH', url: URL_FOTO });
    const enviado = (subida.request.body as FormData).get('archivo') as File;
    expect(enviado.size).toBe(REDUCIDA.size);
    expect(enviado.type).toBe('image/png');
    expect(enviado.name).toBe('foto.png');
    subida.flush({ imagen: FOTO, actualizada: '2026-11-08T10:00:00' });

    // poner la foto caduca la hoja de vida: se vuelven a pedir sus datos y su PDF, ya con la foto
    await responderHoja(FOTO);
    expect(bloque()!.querySelector('img')!.getAttribute('src')).toBe(FOTO);
  });

  it('quitarla la borra y el recuadro vuelve a decir que no hay', async () => {
    montar();
    await abrir(FOTO);

    boton('Quitar')!.click();
    await asentar(fixture);
    http
      .expectOne({ method: 'DELETE', url: URL_FOTO })
      .flush(null, { status: 204, statusText: 'No Content' });
    await responderHoja(null);

    expect(bloque()!.querySelector('[data-sin-foto]')).not.toBeNull();
  });

  it('si la imagen no se puede leer lo dice y no sube nada', async () => {
    montar();
    await abrir();
    reducir = async () => {
      throw new Error('no es una imagen');
    };

    await elegir(new File(['dañada'], 'foto.jpg', { type: 'image/jpeg' }));
    await guardarEncuadre();

    expect(bloque()!.querySelector('[role="alert"]')!.textContent?.trim()).toBe(
      'No se pudo leer la foto. Elija una imagen.',
    );
    http.expectNone({ method: 'PATCH', url: URL_FOTO });
  });

  it('lo que no es una imagen ni se intenta reducir: lo dice y no sube nada', async () => {
    montar();
    await abrir();

    await elegir(new File(['texto'], 'notas.txt', { type: 'text/plain' }));

    expect(reducidas).toEqual([]);
    expect(bloque()!.querySelector('[role="alert"]')!.textContent?.trim()).toBe(
      'Eso no es una imagen. Elija una foto.',
    );
    http.expectNone({ method: 'PATCH', url: URL_FOTO });
  });

  /** jsdom no implementa DataTransfer: el evento lleva sus archivos puestos a mano. */
  function evento(
    tipo: string,
    propiedad: 'dataTransfer' | 'clipboardData',
    archivos: File[],
  ): Event {
    const e = new Event(tipo, { bubbles: true, cancelable: true });
    Object.defineProperty(e, propiedad, { value: { files: archivos } });
    return e;
  }

  it('una foto soltada en el bloque se reduce y se sube, y el bloque se marca mientras se arrastra', async () => {
    montar();
    await abrir();
    const foto = new File(['foto'], 'arrastrada.jpg', { type: 'image/jpeg' });

    const encima = evento('dragover', 'dataTransfer', [foto]);
    bloque()!.dispatchEvent(encima);
    await asentar(fixture);
    expect(encima.defaultPrevented).toBe(true);
    expect(bloque()!.classList).toContain('outline-dashed');

    const suelta = evento('drop', 'dataTransfer', [foto]);
    bloque()!.dispatchEvent(suelta);
    await asentar(fixture);

    expect(suelta.defaultPrevented).toBe(true);
    expect(bloque()!.classList).not.toContain('outline-dashed');
    expect(encuadre()!.dataset['archivo']).toBe('arrastrada.jpg');
    await guardarEncuadre();
    expect(reducidas).toEqual([RECORTE]);
    http.expectOne({ method: 'PATCH', url: URL_FOTO }).flush({ imagen: FOTO });
    await responderHoja(FOTO);
  });

  it('Ctrl+V con una imagen en el portapapeles la pone como foto', async () => {
    montar();
    await abrir();
    const captura = new File(['captura'], 'image.png', { type: 'image/png' });

    const pegado = evento('paste', 'clipboardData', [captura]);
    window.dispatchEvent(pegado);
    await asentar(fixture);

    expect(pegado.defaultPrevented).toBe(true);
    expect(encuadre()!.dataset['archivo']).toBe('image.png');
    await guardarEncuadre();
    expect(reducidas).toEqual([RECORTE]);
    http.expectOne({ method: 'PATCH', url: URL_FOTO }).flush({ imagen: FOTO });
    await responderHoja(FOTO);
  });

  it('un pegado sin imagen no se toca: sigue su camino', async () => {
    montar();
    await abrir();

    const pegado = evento('paste', 'clipboardData', []);
    window.dispatchEvent(pegado);
    await asentar(fixture);

    expect(pegado.defaultPrevented).toBe(false);
    expect(reducidas).toEqual([]);
  });

  it('con el encuadre abierto, pegar otra imagen no lo reemplaza', async () => {
    montar();
    await abrir();
    await elegir(new File(['primera'], 'primera.jpg', { type: 'image/jpeg' }));

    window.dispatchEvent(
      evento('paste', 'clipboardData', [new File(['otra'], 'otra.png', { type: 'image/png' })]),
    );
    await asentar(fixture);

    expect(encuadre()!.dataset['archivo']).toBe('primera.jpg');
  });

  it('pegar un archivo que no es una imagen tampoco se toca, ni avisa de nada', async () => {
    montar();
    await abrir();

    const pegado = evento('paste', 'clipboardData', [
      new File(['notas'], 'notas.txt', { type: 'text/plain' }),
    ]);
    window.dispatchEvent(pegado);
    await asentar(fixture);

    expect(pegado.defaultPrevented).toBe(false);
    expect(bloque()!.querySelector('[role="alert"]')).toBeNull();
  });

  it('quien solo lee no pone una foto pegándola', async () => {
    montar(['equipment.read']);
    fixture.detectChanges();
    await asentar(fixture);
    http.expectOne(URL_PDF).flush(PDF);
    await asentar(fixture);

    window.dispatchEvent(
      evento('paste', 'clipboardData', [new File(['x'], 'a.png', { type: 'image/png' })]),
    );
    await asentar(fixture);

    expect(reducidas).toEqual([]);
    http.expectNone({ method: 'PATCH', url: URL_FOTO });
  });

  it('si el servidor la rechaza, lo avisa', async () => {
    montar();
    await abrir();

    await elegir(new File(['x'], 'foto.png', { type: 'image/png' }));
    await guardarEncuadre();
    http
      .expectOne({ method: 'PATCH', url: URL_FOTO })
      .flush({ code: 'ERR_EQUIPMENT_007' }, { status: 400, statusText: 'Bad Request' });
    await asentar(fixture);

    expect(bloque()!.querySelector('[role="alert"]')).not.toBeNull();
  });
});
