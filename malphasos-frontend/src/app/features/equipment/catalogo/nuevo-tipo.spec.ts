import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, elegirEnBuscador, responderA } from '../../../../testing/pantalla';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import { TIPOS, URL_TIPOS } from '../../../../testing/catalogo';
import { NuevoTipo } from './nuevo-tipo';

@Component({ selector: 'app-catalogo-falso', template: '' })
class CatalogoFalso {}

describe('Alta de un tipo de equipo', () => {
  let fixture: ComponentFixture<NuevoTipo>;
  let http: HttpTestingController;
  let desinstalarAlmacenamiento: () => void;

  beforeEach(async () => {
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'catalogo', component: CatalogoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoTipo);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // La pantalla consulta los tipos ya registrados para sugerir sus tecnologias.
    await responderA(fixture, http, URL_TIPOS, TIPOS);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  /** Vuelve a montar la pantalla con otros tipos registrados, de los que salen las autorizadas. */
  async function remontar(tipos: object[]): Promise<void> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'catalogo', component: CatalogoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoTipo);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await responderA(fixture, http, URL_TIPOS, tipos);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function rellenarObligatorios(): void {
    escribir('nombre', 'Tensiómetro');
    // La tecnologia es un campo de busqueda con texto libre: se escribe igual que en un input.
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

  it('las cotas salen del contrato: 50 en nombre, 250 en los textos largos', () => {
    // La tecnologia no lleva cota y no hace falta: al ser lista cerrada, no se puede escribir un valor
    // que no exista, y todos los autorizados caben.
    expect(raiz().querySelector<HTMLInputElement>('#nombre')!.maxLength).toBe(50);
    expect(raiz().querySelector<HTMLTextAreaElement>('#definicionTecnica')!.maxLength).toBe(250);
    expect(raiz().querySelector<HTMLTextAreaElement>('#recomendacionesCuidado')!.maxLength).toBe(250);
  });

  describe('La tecnología es una lista cerrada', () => {
    it('ofrece lo que la empresa ya usa antes que las autorizadas de partida', async () => {
      // El tipo registrado en la prueba usa «Electrónica»: es la grafia que agrupa con lo que hay.
      const campo = raiz().querySelector<HTMLInputElement>('#tecnologiaPredominante')!;
      campo.dispatchEvent(new Event('focus'));
      campo.value = 'electr';
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await asentar(fixture);

      const opciones = [
        ...raiz().querySelectorAll('#tecnologiaPredominante-lista [role="option"]'),
      ].map((o) => o.textContent?.trim());

      expect(opciones[0]).toBe('Electrónica');
      expect(opciones).toContain('Electromecánica');
    });

    it('no deja poner una tecnología que no esté autorizada', async () => {
      // Es una lista cerrada por decision del usuario: con texto libre, la misma tecnologia acaba
      // escrita de cuatro maneras y agrupar por ella deja de servir. El backend aceptaria cualquier
      // cosa, asi que la restriccion tiene que estar aqui.
      escribir('nombre', 'Bomba de infusión');
      escribir('tecnologiaPredominante', 'Peristáltica');
      escribir('definicionTecnica', 'Infunde a caudal constante');
      escribir('recomendacionesCuidado', 'Revisar el equipo de infusión');
      await enviar();

      http.expectNone({ method: 'POST', url: URL_TIPOS });
      expect(texto()).toContain('No hay ninguna opción con ese nombre');
    });

    it('una tecnología que la empresa ya usa está autorizada, aunque no sea de la lista de partida', async () => {
      // «Peristáltica» NO esta entre las autorizadas de partida: solo cuenta porque ya hay un tipo
      // registrado con ella. Sin esto, abrir un tipo antiguo dejaria el campo en blanco y el
      // formulario invalido sin explicar nada.
      //
      // Y la prueba usa a proposito un valor que NO esta en la lista de partida: con «Electrónica»
      // habria pasado igual de las dos formas, que es una prueba que no mira nada.
      await remontar([{ ...TIPOS[0], id: 't5', tecnologiaPredominante: 'Peristáltica' }]);

      escribir('nombre', 'Bomba de infusión');
      escribir('tecnologiaPredominante', 'Peristáltica');
      escribir('definicionTecnica', 'Infunde a caudal constante');
      escribir('recomendacionesCuidado', 'Revisar el equipo de infusión');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

      expect(alta.request.body.tecnologiaPredominante).toBe('Peristáltica');
      alta.flush({ id: 't9' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('elegir una sugerencia la escribe tal cual', async () => {
      await elegirEnBuscador(fixture, 'tecnologiaPredominante', 'Neumática');

      expect(raiz().querySelector<HTMLInputElement>('#tecnologiaPredominante')!.value).toBe(
        'Neumática',
      );
    });
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

  afterEach(() => {
    http.verify();
    desinstalarAlmacenamiento();
  });
});
