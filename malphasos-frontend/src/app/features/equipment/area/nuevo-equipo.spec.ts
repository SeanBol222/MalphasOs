import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import { ID_MODELO, responderAlCatalogo } from '../../../../testing/catalogo';
import { NuevoEquipo } from './nuevo-equipo';

const ID_AREA = 'a1';
const URL_AREA = `http://localhost:8081/v1/api/service-areas/${ID_AREA}`;
const URL_EQUIPOS_DEL_AREA = `${URL_AREA}/equipments`;

const AREA = { id: ID_AREA, nombre: 'Urgencias', idSede: 's1', estadoActivo: true };

@Component({ selector: 'app-ficha-area-falsa', template: '' })
class FichaAreaFalsa {}

describe('Registro de un equipo en un area', () => {
  let fixture: ComponentFixture<NuevoEquipo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'areas/:id', component: FichaAreaFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoEquipo);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_AREA);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(datos: Parameters<typeof responderAlCatalogo>[2] = {}): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_AREA, AREA);
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

  it('dice en que area se instala', async () => {
    await abrir();

    expect(texto()).toContain('Urgencias');
  });

  it('el modelo se elige por su nombre compuesto, no por su identificador', async () => {
    // Cuatro listas del catalogo para una etiqueta. Un desplegable de UUID no se puede usar.
    await abrir();

    const opciones = [...raiz().querySelectorAll<HTMLOptionElement>('#idModelo option')];

    expect(opciones.map((o) => o.textContent?.trim())).toEqual([
      'Elija un modelo',
      'Tensiómetro · Welch Allyn · Medtronic',
    ]);
  });

  it('sin modelos en el catalogo lo explica y enlaza al catalogo', async () => {
    // Un desplegable vacio dejaria a quien lo abre sin saber que hacer, y la causa no esta en esta
    // pantalla sino en la cadena del catalogo.
    await abrir({ modelos: [] });

    expect(texto()).toContain('No hay modelos en el catálogo');
    expect([...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'))).toContain(
      '/catalogo',
    );
  });

  it('manda el modelo y la serie, que es lo unico obligatorio', async () => {
    await abrir();
    escribir('idModelo', ID_MODELO);
    escribir('serie', 'SN-0001');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });

    expect(alta.request.body).toEqual({ idModelo: ID_MODELO, serie: 'SN-0001' });
    alta.flush({ id: 'ec1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('los datos opcionales vacios no viajan', async () => {
    // Una cadena vacia no es un numero de inventario y una fecha vacia no es una fecha de compra:
    // mandarlas haria que el backend guardara un dato inventado.
    await abrir();
    escribir('idModelo', ID_MODELO);
    escribir('serie', 'SN-0001');
    escribir('numeroInventario', '');
    escribir('fechaCompra', '');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });

    expect(alta.request.body).not.toHaveProperty('numeroInventario');
    expect(alta.request.body).not.toHaveProperty('fechaCompra');
    expect(alta.request.body).not.toHaveProperty('valorCompra');
    alta.flush({ id: 'ec1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('con los opcionales rellenos, los manda tal como el contrato los declara', async () => {
    await abrir();
    escribir('idModelo', ID_MODELO);
    escribir('serie', 'SN-0001');
    escribir('numeroInventario', 'INV-77');
    escribir('fechaCompra', '2026-01-15');
    escribir('valorCompra', '1500000');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });

    expect(alta.request.body).toEqual({
      idModelo: ID_MODELO,
      serie: 'SN-0001',
      numeroInventario: 'INV-77',
      fechaCompra: '2026-01-15',
      valorCompra: 1500000,
    });
    alta.flush({ id: 'ec1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin serie no llega al servidor, y dice por que importa', async () => {
    await abrir();
    escribir('idModelo', ID_MODELO);
    await enviar();

    http.expectNone({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });
    expect(texto()).toContain('es lo que identifica a esta máquina');
  });

  it('al guardar vuelve a la ficha del area', async () => {
    await abrir();
    escribir('idModelo', ID_MODELO);
    escribir('serie', 'SN-0001');
    await enviar();
    http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA }).flush({ id: 'ec1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe(`/areas/${ID_AREA}`);
  });

  it('un fallo del API se ensena traducido, con el codigo real del modulo', async () => {
    await abrir();
    escribir('idModelo', ID_MODELO);
    escribir('serie', 'SN-0001');
    await enviar();
    http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA }).flush(
      { code: 'ERR_EQUIPMENT_009', message: 'Service area not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    await asentar(fixture);

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Esa área de servicio no existe');
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('cada campo tiene su etiqueta asociada', async () => {
      await abrir();

      for (const id of ['idModelo', 'serie', 'numeroInventario', 'fechaCompra', 'valorCompra']) {
        expect(raiz().querySelector(`label[for="${id}"]`)).toBeTruthy();
      }
    });

    it('los controles respetan el area tactil minima', async () => {
      await abrir();

      for (const control of raiz().querySelectorAll('input, select, button, form a')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });
  });

  afterEach(() => http.verify());
});
