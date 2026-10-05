import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { FichaDeModelo } from './ficha-de-modelo';

const ID = 'mo1';
const URL_MODELO = `http://localhost:8081/v1/api/models/${ID}`;
const URL_FICHA = `${URL_MODELO}/technical-sheet`;

function modelo(fichaTecnica: object = {}): object {
  return {
    id: ID,
    nombre: 'GS14',
    idEquipo: 'e1',
    idFabricante: 'f1',
    estadoActivo: true,
    fichaTecnica,
  };
}

/** El destino al guardar: solo hace falta que la ruta exista. */
@Component({ template: '' })
class ListaFalsa {}

describe('Ficha tecnica de un modelo', () => {
  let fixture: ComponentFixture<FichaDeModelo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
        provideRouter([{ path: 'catalogo/modelos', component: ListaFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(FichaDeModelo);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const campo = (id: string) =>
    raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;

  async function abrir(ficha: object = {}): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_MODELO, modelo(ficha));
  }

  function escribir(id: string, valor: string): void {
    const control = campo(id);
    control.value = valor;
    control.dispatchEvent(new Event(control instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  async function guardar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('se abre con la ficha que el modelo ya tiene', async () => {
    await abrir({ riesgo: 'IIB', voltaje: 110, amperaje: 2.5, alimentacion: 'Red eléctrica' });

    expect(raiz().textContent).toContain('del modelo GS14');
    expect(campo('riesgo').value).toBe('IIB');
    expect(campo('voltaje').value).toBe('110');
    expect(campo('amperaje').value).toBe('2.5');
    expect(campo('alimentacion').value).toBe('Red eléctrica');
  });

  it('las clases de riesgo se leen como se escriben, IIa y no IIA', async () => {
    await abrir();

    const etiquetas = [...raiz().querySelectorAll('#riesgo option')].map((o) =>
      o.textContent?.trim(),
    );
    expect(etiquetas).toEqual(['Sin clasificar', 'Clase I', 'Clase IIa', 'Clase IIb', 'Clase III']);
  });

  it('guarda la ficha entera, sin mandar lo vacio, y vuelve a la lista de modelos', async () => {
    // El backend reemplaza la ficha: lo que no viaja queda vacio. Por eso un voltaje borrado se borra
    // no mandandolo, y no mandando un cero, que el servidor rechazaria.
    await abrir({ riesgo: 'I', voltaje: 220 });
    escribir('voltaje', '');
    escribir('potencia', '45');
    escribir('caracteristicas', '  Pantalla LCD  ');
    await guardar();

    const cambio = http.expectOne({ method: 'PATCH', url: URL_FICHA });
    expect(cambio.request.body).toEqual({
      riesgo: 'I',
      potencia: 45,
      caracteristicas: 'Pantalla LCD',
    });
    cambio.flush(modelo());
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe('/catalogo/modelos');
  });

  it('guardar la ficha deja caducada la hoja de vida, que imprime esos datos', async () => {
    // La hoja de vida lee la ficha del modelo. Sin esto, una hoja ya en cache seguiria imprimiendo el
    // voltaje viejo hasta recargar. La invalidacion es de todo el catalogo, y esta es la escritura que
    // mas se nota en el papel.
    const cache = TestBed.inject(QueryClient);
    // gcTime 0 en el cliente de pruebas: sin esto la consulta sembrada desaparece antes de guardar.
    cache.setQueryDefaults(['hoja-de-vida'], { gcTime: Infinity });
    cache.setQueryData(['hoja-de-vida', 'cualquier-equipo'], { tecnica: {} });
    await abrir();
    escribir('voltaje', '110');
    await guardar();

    http.expectOne({ method: 'PATCH', url: URL_FICHA }).flush(modelo({ voltaje: 110 }));
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(cache.getQueryState(['hoja-de-vida', 'cualquier-equipo'])?.isInvalidated).toBe(true);
  });

  it('una corriente con tres decimales o un voltaje en cero no llegan al servidor', async () => {
    await abrir();
    escribir('amperaje', '1.255');
    await guardar();

    http.expectNone({ method: 'PATCH', url: URL_FICHA });
    expect(raiz().textContent).toContain('la corriente admite dos decimales');

    escribir('amperaje', '');
    escribir('voltaje', '0');
    await guardar();

    http.expectNone({ method: 'PATCH', url: URL_FICHA });
  });

  it('no tiene campo de tipo de equipo: la ficha es solo de este modelo', async () => {
    await abrir();

    expect(raiz().querySelector('#uso, #idTipo, #limpiezaCotidiana')).toBeNull();
  });
});
