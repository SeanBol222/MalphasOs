import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import {
  ID_EQUIPO,
  ID_FABRICANTE,
  responderAlCatalogo,
  URL_MODELOS,
} from '../../../../testing/catalogo';
import { Modelos } from './modelos';

describe('Modelos del catalogo', () => {
  let fixture: ComponentFixture<Modelos>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
      ],
    });
    fixture = TestBed.createComponent(Modelos);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(datos: Parameters<typeof responderAlCatalogo>[2] = {}): Promise<void> {
    await responderAlCatalogo(fixture, http, datos);
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

  it('dice que un modelo es lo unico que se puede instalar', async () => {
    // Es la causa mas probable de que el alta de un equipo aparezca sin opciones.
    await abrir();

    expect(texto()).toContain('Es lo único que se puede instalar');
  });

  it('una fila se lee cruzando cuatro listas: tipo, marca, fabricante e INVIMA', async () => {
    await abrir();

    const fila = raiz().querySelector('li')!.textContent ?? '';

    expect(fila).toContain('Tensiómetro');
    expect(fila).toContain('Welch Allyn');
    expect(fila).toContain('Medtronic');
    expect(fila).toContain('INVIMA INV-1');
  });

  it('el desplegable de equipos ofrece la etiqueta legible y no el identificador', async () => {
    await abrir();

    const opciones = [...raiz().querySelectorAll<HTMLOptionElement>('#equipoDelModelo option')];

    expect(opciones.map((o) => o.textContent?.trim())).toEqual([
      'Elija un equipo',
      'Tensiómetro · Welch Allyn',
    ]);
  });

  it('sin nombre no llega al servidor', async () => {
    // La validacion del formulario corre antes: es lo que evita un 400 que nadie sabria leer.
    await abrir();
    escribir('equipoDelModelo', ID_EQUIPO);
    escribir('fabricanteDelModelo', ID_FABRICANTE);
    await enviar();

    http.expectNone({ method: 'POST', url: URL_MODELOS });
  });

  it('crear un modelo con INVIMA manda los cuatro campos', async () => {
    await abrir();
    escribir('nombreDelModelo', 'IdeaPad 3');
    escribir('equipoDelModelo', ID_EQUIPO);
    escribir('fabricanteDelModelo', ID_FABRICANTE);
    escribir('invimaDelModelo', 'INV-2');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_MODELOS });

    expect(alta.request.body).toEqual({
      nombre: 'IdeaPad 3',
      idEquipo: ID_EQUIPO,
      idFabricante: ID_FABRICANTE,
      invima: 'INV-2',
    });
    alta.flush({ id: 'mo2' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin INVIMA la clave no viaja, pero el nombre si: no son simetricos', async () => {
    // Un modelo SIN registro sanitario es un estado normal mientras se tramita; uno sin nombre no es
    // nada, y el servidor lo rechaza con la columna.
    await abrir();
    escribir('nombreDelModelo', 'IdeaPad 3');
    escribir('equipoDelModelo', ID_EQUIPO);
    escribir('fabricanteDelModelo', ID_FABRICANTE);
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_MODELOS });

    expect(alta.request.body).not.toHaveProperty('invima');
    expect(alta.request.body.nombre).toBe('IdeaPad 3');
    alta.flush({ id: 'mo2' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin equipo y fabricante no llega al servidor', async () => {
    await abrir();
    await enviar();

    http.expectNone({ method: 'POST', url: URL_MODELOS });
    expect(texto()).toContain('Elija un equipo del catálogo y un fabricante');
  });

  afterEach(() => http.verify());
});
