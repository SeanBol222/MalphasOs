import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  EQUIPOS,
  ID_FABRICANTE,
  ID_MARCA,
  ID_TIPO,
  responderAlCatalogo,
  URL_EQUIPOS,
  URL_FABRICANTES,
  URL_MARCAS,
  URL_MODELOS,
  URL_TIPOS,
} from '../../../../testing/catalogo';
import { PanelDeModelo } from './panel-de-modelo';

const URL_PAISES = 'http://localhost:8081/v1/api/countries';

/** Recoge lo que el panel emite, que es lo que el formulario padre haria con ello. */
@Component({
  selector: 'app-padre-falso',
  imports: [PanelDeModelo],
  template: `<app-panel-de-modelo (creado)="recibido.set($event)" />`,
})
class PadreFalso {
  readonly recibido = signal('');
}

describe('Panel para crear un modelo sin salir del alta', () => {
  let fixture: ComponentFixture<PadreFalso>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: proveerApiSimulado() });
    fixture = TestBed.createComponent(PadreFalso);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(datos: Parameters<typeof responderAlCatalogo>[2] = {}): Promise<void> {
    await responderAlCatalogo(fixture, http, datos);
    await responderA(fixture, http, URL_PAISES, []);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function pulsarCrear(): void {
    [...raiz().querySelectorAll('button')]
      .find((b) => (b.textContent ?? '').includes('Crear modelo'))!
      .click();
    fixture.detectChanges();
  }

  it('ofrece crear cada pieza desde su propio desplegable', async () => {
    await abrir();

    for (const id of ['idTipo', 'idMarca', 'idFabricante']) {
      const opciones = [...raiz().querySelectorAll<HTMLOptionElement>(`#${id} option`)];

      expect(opciones.some((o) => o.value === 'nuevo')).toBe(true);
    }
  });

  it('con las tres piezas existentes, reutiliza la combinacion y solo crea el modelo', async () => {
    // La combinacion de tipo y marca ya existe en el catalogo: crear otra igual dejaria dos entradas
    // identicas y ningun modo de saber cual usar.
    await abrir();
    escribir('idTipo', ID_TIPO);
    escribir('idMarca', ID_MARCA);
    escribir('idFabricante', ID_FABRICANTE);
    pulsarCrear();
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL_TIPOS });
    http.expectNone({ method: 'POST', url: URL_MARCAS });
    http.expectNone({ method: 'POST', url: URL_FABRICANTES });
    http.expectNone({ method: 'POST', url: URL_EQUIPOS });

    const alta = http.expectOne({ method: 'POST', url: URL_MODELOS });

    expect(alta.request.body).toEqual({ idEquipo: EQUIPOS[0].id, idFabricante: ID_FABRICANTE });
    alta.flush({ id: 'mo9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    // Y el padre se queda con el modelo nuevo: es lo que se vino a hacer.
    expect(fixture.componentInstance.recibido()).toBe('mo9');
  });

  it('si la combinacion de tipo y marca no existe, la crea antes del modelo', async () => {
    await abrir({ equipos: [] });
    escribir('idTipo', ID_TIPO);
    escribir('idMarca', ID_MARCA);
    escribir('idFabricante', ID_FABRICANTE);
    pulsarCrear();
    await asentar(fixture);

    const equipo = http.expectOne({ method: 'POST', url: URL_EQUIPOS });

    expect(equipo.request.body).toEqual({ idTipoEquipo: ID_TIPO, idMarca: ID_MARCA });
    equipo.flush({ id: 'e9' });
    await asentar(fixture);

    const alta = http.expectOne({ method: 'POST', url: URL_MODELOS });

    expect(alta.request.body).toEqual({ idEquipo: 'e9', idFabricante: ID_FABRICANTE });
    alta.flush({ id: 'mo9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('crea las piezas nuevas en orden y encadena sus identificadores', async () => {
    // El orden no es casual: cada paso necesita el identificador del anterior, asi que van en serie.
    await abrir({ equipos: [] });
    escribir('idTipo', 'nuevo');
    escribir('nombreDeTipo', 'Desfibrilador');
    escribir('tecnologiaDeTipo', 'Electrónica');
    escribir('definicionDeTipo', 'Aplica descargas');
    escribir('cuidadoDeTipo', 'Revisar batería');
    escribir('idMarca', 'nuevo');
    escribir('nombreDeMarca', 'Philips');
    escribir('idFabricante', 'nuevo');
    escribir('nombreDeFabricante', 'Philips Healthcare');
    pulsarCrear();
    await asentar(fixture);

    const tipo = http.expectOne({ method: 'POST', url: URL_TIPOS });

    expect(tipo.request.body).toEqual({
      nombre: 'Desfibrilador',
      tecnologiaPredominante: 'Electrónica',
      definicionTecnica: 'Aplica descargas',
      recomendacionesCuidado: 'Revisar batería',
    });
    tipo.flush({ id: 't9' });
    await asentar(fixture);

    http.expectOne({ method: 'POST', url: URL_MARCAS }).flush({ id: 'm9' });
    await asentar(fixture);

    const fabricante = http.expectOne({ method: 'POST', url: URL_FABRICANTES });

    // Sin pais elegido la clave no viaja, igual que en el resto de la aplicacion.
    expect(fabricante.request.body).toEqual({ nombre: 'Philips Healthcare' });
    fabricante.flush({ id: 'f9' });
    await asentar(fixture);

    const equipo = http.expectOne({ method: 'POST', url: URL_EQUIPOS });

    expect(equipo.request.body).toEqual({ idTipoEquipo: 't9', idMarca: 'm9' });
    equipo.flush({ id: 'e9' });
    await asentar(fixture);

    const modelo = http.expectOne({ method: 'POST', url: URL_MODELOS });

    expect(modelo.request.body).toEqual({ idEquipo: 'e9', idFabricante: 'f9' });
    modelo.flush({ id: 'mo9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(fixture.componentInstance.recibido()).toBe('mo9');
  });

  it('avisa de que las piezas nuevas quedan creadas para todos', async () => {
    await abrir();
    escribir('idMarca', 'nuevo');

    expect(texto()).toContain('Se creará también una marca');
    expect(texto()).toContain('Queda en el catálogo');
  });

  it('un tipo nuevo sin sus cuatro datos no manda nada, y lo dice', async () => {
    await abrir();
    escribir('idTipo', 'nuevo');
    escribir('nombreDeTipo', 'Desfibrilador');
    escribir('idMarca', ID_MARCA);
    escribir('idFabricante', ID_FABRICANTE);
    pulsarCrear();
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL_TIPOS });
    expect(texto()).toContain('De un tipo nuevo hacen falta sus cuatro datos');
  });

  it('si falla un paso, lo dice y avisa de que lo anterior ya esta creado', async () => {
    // Son cinco llamadas sin transaccion: reintentar a ciegas acaba con la misma marca tres veces.
    await abrir({ equipos: [] });
    escribir('idTipo', ID_TIPO);
    escribir('idMarca', 'nuevo');
    escribir('nombreDeMarca', 'Philips');
    escribir('idFabricante', ID_FABRICANTE);
    pulsarCrear();
    await asentar(fixture);

    http.expectOne({ method: 'POST', url: URL_MARCAS }).flush({ id: 'm9' });
    await asentar(fixture);
    http.expectOne({ method: 'POST', url: URL_EQUIPOS }).flush(
      { code: 'ERR_EQUIPMENT_007', message: 'Invalid equipment data', details: [] },
      { status: 400, statusText: 'Bad Request' },
    );
    await asentar(fixture);

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Revise los datos del equipo');
    expect(aviso?.textContent).toContain('sigue creado');
    // Y no se emite nada: el padre no debe quedarse con un modelo que no existe.
    expect(fixture.componentInstance.recibido()).toBe('');
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  afterEach(() => http.verify());
});
