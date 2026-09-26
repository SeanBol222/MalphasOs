import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar } from '../../../../testing/pantalla';
import { URL_TIPOS } from '../../../../testing/catalogo';
import { NuevoTipo } from './nuevo-tipo';

@Component({ selector: 'app-catalogo-falso', template: '' })
class CatalogoFalso {}

describe('Alta de un tipo de equipo', () => {
  let fixture: ComponentFixture<NuevoTipo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'catalogo', component: CatalogoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoTipo);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function rellenarObligatorios(): void {
    escribir('nombre', 'Tensiómetro');
    escribir('tecnologiaPredominante', 'Electrónica');
    escribir('definicionTecnica', 'Mide presión arterial');
    escribir('recomendacionesCuidado', 'No golpear');
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('dice cuales campos son opcionales, en vez de dejarlo para el momento de guardar', () => {
    expect(texto()).toContain('Datos opcionales');
    expect(texto()).toContain('ninguno impide guardar');
  });

  it('con solo los obligatorios, manda cuatro campos y ni uno mas', async () => {
    // Un cero no es "no se sabe": mandar voltaje 0 o una modalidad vacia haria que el backend
    // guardara un dato inventado.
    rellenarObligatorios();
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

    expect(alta.request.body).toEqual({
      nombre: 'Tensiómetro',
      tecnologiaPredominante: 'Electrónica',
      definicionTecnica: 'Mide presión arterial',
      recomendacionesCuidado: 'No golpear',
    });
    alta.flush({ id: 't9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('con los opcionales rellenos, los manda con su tipo', async () => {
    rellenarObligatorios();
    escribir('modalidadVerificacion', 'EQUIPO_CONSTANTE');
    escribir('voltaje', '110');
    escribir('amperaje', '2.5');
    escribir('valorUnitarioMantenimiento', '80000');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

    expect(alta.request.body).toEqual({
      nombre: 'Tensiómetro',
      tecnologiaPredominante: 'Electrónica',
      definicionTecnica: 'Mide presión arterial',
      recomendacionesCuidado: 'No golpear',
      modalidadVerificacion: 'EQUIPO_CONSTANTE',
      voltaje: 110,
      amperaje: 2.5,
      valorUnitarioMantenimiento: 80000,
    });
    alta.flush({ id: 't9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin los obligatorios no llega al servidor, y se dice una vez', async () => {
    await enviar();

    http.expectNone({ method: 'POST', url: URL_TIPOS });
    expect(texto()).toContain('son obligatorios');
  });

  it('las cotas salen del contrato: 50 en nombre y tecnologia, 250 en los textos largos', () => {
    expect(raiz().querySelector<HTMLInputElement>('#nombre')!.maxLength).toBe(50);
    expect(raiz().querySelector<HTMLInputElement>('#tecnologiaPredominante')!.maxLength).toBe(50);
    expect(raiz().querySelector<HTMLTextAreaElement>('#definicionTecnica')!.maxLength).toBe(250);
    expect(raiz().querySelector<HTMLTextAreaElement>('#recomendacionesCuidado')!.maxLength).toBe(250);
  });

  it('al guardar vuelve al catalogo', async () => {
    rellenarObligatorios();
    await enviar();
    http.expectOne({ method: 'POST', url: URL_TIPOS }).flush({ id: 't9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe('/catalogo');
  });

  afterEach(() => http.verify());
});
