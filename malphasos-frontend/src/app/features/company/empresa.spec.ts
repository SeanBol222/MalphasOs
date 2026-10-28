import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { proveerApiSimulado } from '../../../testing/entorno';
import { proveerSesionFalsa } from '../../../testing/keycloak-falso';
import { asentar, atenderRefresco, elegirEnBuscador, responderA } from '../../../testing/pantalla';
import { Empresa, telefonosDe } from './empresa';

const URL = 'http://localhost:8081/v1/api/company';
const URL_CIUDADES = 'http://localhost:8081/v1/api/cities';
const BOGOTA = 'c0000000-0000-0000-0000-000000000001';
const LIMA = 'c0000000-0000-0000-0000-000000000002';

/** El catálogo de ciudades, del que sale la de la empresa desde V44. */
const CIUDADES = [
  { id: BOGOTA, nombre: 'Bogotá', idPais: 'co', estadoActivo: true },
  { id: LIMA, nombre: 'Lima', idPais: 'pe', estadoActivo: true },
];

/** Una empresa inventada: MalphasOS no lleva datos de ninguna empresa real, tampoco en sus pruebas. */
const EMPRESA = {
  id: 'e1',
  nombre: 'Servicios Biomédicos de Ejemplo S.A.S.',
  nit: '900.000.000-0',
  telefonos: ['601 000 0000', '601 000 0001'],
  garantia: 'La garantía cubre cinco días.',
  codigoInforme: 'FT-002',
};

/** La empresa que presta el servicio (2026-10-17): sus datos y su logo, editables por un administrador. */
describe('Empresa', () => {
  let fixture: ComponentFixture<Empresa>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = ['company.read', 'company.write']): void {
    TestBed.configureTestingModule({
      providers: [...proveerApiSimulado(), ...proveerSesionFalsa({ autoridades })],
    });
    fixture = TestBed.createComponent(Empresa);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const campo = (id: string) => raiz().querySelector<HTMLInputElement>(`#empresa-${id}`)!;
  const escribir = (id: string, valor: string) => {
    campo(id).value = valor;
    campo(id).dispatchEvent(new Event('input'));
  };
  const boton = (texto: string) =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === texto) as
      HTMLButtonElement | undefined;

  async function abrir(empresa: object | null = EMPRESA): Promise<void> {
    fixture.detectChanges();
    if (empresa) {
      await responderA(fixture, http, URL, empresa);
    } else {
      await responderA(
        fixture,
        http,
        URL,
        { code: 'ERR_COMPANY_001', message: 'The service company has not been configured' },
        { status: 404, statusText: 'Not Found' },
      );
    }
    await responderA(fixture, http, URL_CIUDADES, CIUDADES);
  }

  it('llena el formulario con lo guardado, los teléfonos separados por comas', async () => {
    montar();
    await abrir();

    expect(campo('nombre').value).toBe('Servicios Biomédicos de Ejemplo S.A.S.');
    expect(campo('telefonos').value).toBe('601 000 0000, 601 000 0001');
    expect(raiz().querySelector<HTMLTextAreaElement>('#empresa-garantia')!.value).toBe(
      'La garantía cubre cinco días.',
    );
  });

  it('la ciudad sale del catálogo: se ve por su nombre y se guarda por su identificador (V44)', async () => {
    montar();
    await abrir({ ...EMPRESA, idCiudad: BOGOTA });

    expect(campo('ciudad').value).toBe('Bogotá');

    await elegirEnBuscador(fixture, 'empresa-ciudad', 'Lima');
    boton('Guardar datos')!.click();
    await asentar(fixture);

    const guardado = http.expectOne({ method: 'PATCH', url: URL });
    expect(guardado.request.body.idCiudad).toBe(LIMA);
    guardado.flush({ ...EMPRESA, idCiudad: LIMA });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL, { ...EMPRESA, idCiudad: LIMA });
  });

  it('guardar manda todo recortado, los teléfonos como lista y lo vacío ausente', async () => {
    montar();
    await abrir();
    escribir('telefonos', ' 601 1 , , 601 2 ');
    const garantia = raiz().querySelector<HTMLTextAreaElement>('#empresa-garantia')!;
    garantia.value = '  Nueva política de garantía.  ';
    garantia.dispatchEvent(new Event('input'));
    await asentar(fixture);
    boton('Guardar datos')!.click();
    await asentar(fixture);

    const guardado = http.expectOne({ method: 'PATCH', url: URL });
    expect(guardado.request.body).toEqual({
      nombre: 'Servicios Biomédicos de Ejemplo S.A.S.',
      nit: '900.000.000-0',
      telefonos: ['601 1', '601 2'],
      garantia: 'Nueva política de garantía.',
      codigoInforme: 'FT-002',
    });
    guardado.flush({ ...EMPRESA, garantia: 'Nueva política de garantía.' });
    await asentar(fixture);
    // La recarga trae lo recién guardado, como en el servidor de verdad: con los datos viejos la caché
    // no cambiaría y el formulario no se rellenaría, y el aviso sobrevivía por eso y no por el código.
    await atenderRefresco(fixture, http, URL, { ...EMPRESA, garantia: 'Nueva política de garantía.' });

    expect(raiz().textContent).toContain('Datos guardados.');

    // Y lo que se escribe después ya no está guardado: el aviso se va.
    escribir('movil', '300 111 1111');
    await asentar(fixture);
    expect(raiz().textContent).not.toContain('Datos guardados.');
  });

  it('guardar deja vieja la hoja de vida en caché: imprime el membrete', async () => {
    montar();
    const cache = TestBed.inject(QueryClient);
    // El cliente de pruebas tiene gcTime 0: sin esto la consulta sembrada desaparece antes de guardar.
    cache.setQueryDefaults(['hoja-de-vida'], { gcTime: Infinity });
    cache.setQueryData(['hoja-de-vida', 'cualquier-equipo'], { empresa: EMPRESA });
    await abrir();
    boton('Guardar datos')!.click();
    await asentar(fixture);

    http.expectOne({ method: 'PATCH', url: URL }).flush(EMPRESA);
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL, EMPRESA);

    expect(cache.getQueryState(['hoja-de-vida', 'cualquier-equipo'])?.isInvalidated).toBe(true);
  });

  it('una recarga en segundo plano no borra lo que se está escribiendo', async () => {
    montar();
    await abrir();
    escribir('nit', '901.111.111-1');
    await asentar(fixture);

    void TestBed.inject(QueryClient).invalidateQueries({ queryKey: ['empresa'] });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL, {
      ...EMPRESA,
      nombre: 'Otro nombre desde otra pestaña',
    });

    expect(campo('nit').value).toBe('901.111.111-1');
    expect(campo('nombre').value).toBe('Servicios Biomédicos de Ejemplo S.A.S.');
  });

  it('sin nombre no llega al servidor y lo dice', async () => {
    montar();
    await abrir();
    escribir('nombre', '  ');
    await asentar(fixture);
    boton('Guardar datos')!.click();
    await asentar(fixture);

    http.expectNone({ method: 'PATCH', url: URL });
    expect(raiz().textContent).toContain('El nombre es obligatorio.');
  });

  it('más de cinco teléfonos no llegan al servidor', async () => {
    montar();
    await abrir();
    escribir('telefonos', '1, 2, 3, 4, 5, 6');
    await asentar(fixture);
    boton('Guardar datos')!.click();
    await asentar(fixture);

    http.expectNone({ method: 'PATCH', url: URL });
    expect(raiz().textContent).toContain('Caben hasta cinco teléfonos fijos.');
  });

  it('sin configurar lo dice, no como un error, y el logo espera a que se guarden los datos', async () => {
    montar();
    await abrir(null);

    expect(raiz().querySelector('[data-sin-configurar]')).not.toBeNull();
    expect(raiz().querySelector('[role="alert"]')).toBeNull();
    expect(campo('nombre').value).toBe('');
    expect(raiz().querySelector('#empresa-archivo')).toBeNull();
    expect(raiz().textContent).toContain('Guarde primero los datos de la empresa.');
  });

  it('el logo se ve, se sube como archivo y se quita', async () => {
    montar();
    await abrir({ ...EMPRESA, logo: 'data:image/png;base64,iVBORw0KGgo=' });

    expect(raiz().querySelector<HTMLImageElement>('[data-logo]')!.src).toContain('data:image/png');

    const archivo = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'logo.png', {
      type: 'image/png',
    });
    const entrada = raiz().querySelector<HTMLInputElement>('#empresa-archivo')!;
    Object.defineProperty(entrada, 'files', { value: [archivo] });
    entrada.dispatchEvent(new Event('change'));
    await asentar(fixture);

    const subida = http.expectOne({ method: 'PATCH', url: `${URL}/logo` });
    expect((subida.request.body as FormData).get('archivo')).toBe(archivo);
    subida.flush(EMPRESA);
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL, {
      ...EMPRESA,
      logo: 'data:image/png;base64,iVBORw0KGgo=',
    });

    boton('Quitar logo')!.click();
    await asentar(fixture);
    http
      .expectOne({ method: 'DELETE', url: `${URL}/logo` })
      .flush(null, { status: 204, statusText: 'No Content' });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL, EMPRESA);

    expect(raiz().querySelector('[data-logo]')).toBeNull();
    expect(raiz().querySelector('[data-sin-logo]')).not.toBeNull();
  });

  it('un logo rechazado se explica con el catálogo', async () => {
    montar();
    await abrir();
    const entrada = raiz().querySelector<HTMLInputElement>('#empresa-archivo')!;
    Object.defineProperty(entrada, 'files', { value: [new File(['GIF89a'], 'logo.gif')] });
    entrada.dispatchEvent(new Event('change'));
    await asentar(fixture);

    http
      .expectOne({ method: 'PATCH', url: `${URL}/logo` })
      .flush(
        { code: 'ERR_COMPANY_002', message: 'Invalid service company data' },
        { status: 400, statusText: 'Bad Request' },
      );
    await asentar(fixture);

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain('PNG, JPG o SVG');
  });

  it('sin company.write se ve y no se toca', async () => {
    montar(['company.read']);
    await abrir();

    expect(campo('nombre').disabled).toBe(true);
    expect(boton('Guardar datos')).toBeUndefined();
    expect(raiz().querySelector('#empresa-archivo')).toBeNull();
  });

  it('los teléfonos se separan por comas, sin los vacíos', () => {
    expect(telefonosDe(' 1 ,, 2 ,  ')).toEqual(['1', '2']);
    expect(telefonosDe('')).toEqual([]);
  });
});
