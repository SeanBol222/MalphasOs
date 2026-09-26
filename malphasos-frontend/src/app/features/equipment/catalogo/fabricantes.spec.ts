import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { FABRICANTES, URL_FABRICANTES } from '../../../../testing/catalogo';
import { Fabricantes } from './fabricantes';

const URL_PAISES = 'http://localhost:8081/v1/api/countries';
const PAISES = [{ id: 'co', nombre: 'Colombia', codigoIso: 'CO', estadoActivo: true }];

describe('Fabricantes del catalogo', () => {
  let fixture: ComponentFixture<Fabricantes>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
      ],
    });
    fixture = TestBed.createComponent(Fabricantes);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(
    fabricantes: object = FABRICANTES,
    paises: object = PAISES,
    opcionesDePaises?: { status: number; statusText: string },
  ): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_FABRICANTES, fabricantes);
    await responderA(fixture, http, URL_PAISES, paises, opcionesDePaises);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('distingue fabricante de marca, que es la confusion probable', async () => {
    await abrir();

    expect(texto()).toContain('No es lo mismo que la marca');
  });

  it('ensena el pais en palabras', async () => {
    await abrir();

    expect(texto()).toContain('Medtronic');
    expect(texto()).toContain('Colombia');
  });

  it('un fabricante sin pais dice "sin especificar", y uno con pais ilegible dice otra cosa', async () => {
    // Las dos situaciones se veian igual y no son lo mismo: sin location.read el catalogo responde
    // 403, y decir "sin especificar" de un fabricante que si tiene pais es afirmar algo falso.
    await abrir([{ id: 'f2', nombre: 'Sin país', estadoActivo: true }]);

    expect(texto()).toContain('Sin especificar');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read'] }),
      ],
    });
    fixture = TestBed.createComponent(Fabricantes);
    http = TestBed.inject(HttpTestingController);
    await abrir(FABRICANTES, { code: 'ERR_LOCATION_001' }, { status: 403, statusText: 'Forbidden' });

    expect(texto()).toContain('No disponible');
  });

  it('crear un fabricante con pais manda las dos claves', async () => {
    await abrir();
    escribir('fabricanteNuevo', 'Philips');
    escribir('paisFabricante', 'co');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_FABRICANTES });

    expect(alta.request.body).toEqual({ nombre: 'Philips', idPais: 'co' });
    alta.flush({ id: 'f3' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin pais elegido, la clave no viaja', async () => {
    await abrir();
    escribir('fabricanteNuevo', 'Philips');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_FABRICANTES });

    expect(alta.request.body).not.toHaveProperty('idPais');
    alta.flush({ id: 'f3' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  afterEach(() => http.verify());
});
