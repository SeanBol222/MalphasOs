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
      voltaje: 110,
      amperaje: 2.5,
      valorUnitarioMantenimiento: 80000,
    });
    alta.flush({ id: 't9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  describe('Como se verifica', () => {
    /** Elige una modalidad en el bloque de verificacion, que no es parte del formulario reactivo. */
    function elegirModalidad(valor: string): void {
      const campo = raiz().querySelector<HTMLSelectElement>('#modalidadVerificacion')!;
      campo.value = valor;
      campo.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    function escribirEnPunto(indice: number, campo: 'valor' | 'unidad', valor: string): void {
      const entrada = raiz().querySelector<HTMLInputElement>(`#punto-${campo}-${indice}`)!;
      entrada.value = valor;
      entrada.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    it('una modalidad constante pide cuantas lecturas y en que valores', () => {
      // Con patron y equipo variables no hay nada constante que declarar, asi que los campos no estan.
      expect(raiz().querySelector('#cantidadDatos')).toBeNull();

      elegirModalidad('PATRON_EQUIPO_VARIABLE');

      expect(raiz().querySelector('#cantidadDatos')).toBeNull();

      elegirModalidad('PATRON_CONSTANTE');

      expect(raiz().querySelector('#cantidadDatos')).toBeTruthy();
      // Y abre con un punto en blanco, porque hace falta al menos uno.
      expect(raiz().querySelector('#punto-valor-0')).toBeTruthy();
    });

    it('dice que las lecturas son por punto, no en total', () => {
      // Confundirlo daria un reporte con un tercio de los datos que hacian falta.
      elegirModalidad('PATRON_CONSTANTE');

      expect(texto()).toContain('En cada punto se toman estas lecturas');
    });

    it('manda la modalidad con su cantidad y sus puntos', async () => {
      rellenarObligatorios();
      elegirModalidad('PATRON_CONSTANTE');
      escribir('cantidadDatos', '5');
      escribirEnPunto(0, 'valor', '100');
      escribirEnPunto(0, 'unidad', 'mmHg');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

      expect(alta.request.body.modalidadVerificacion).toBe('PATRON_CONSTANTE');
      expect(alta.request.body.cantidadDatos).toBe(5);
      expect(alta.request.body.puntosVerificacion).toEqual([{ valor: 100, unidad: 'mmHg' }]);
      alta.flush({ id: 't9' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('un punto negativo vale: un congelador se verifica a -20 grados', async () => {
      rellenarObligatorios();
      elegirModalidad('EQUIPO_CONSTANTE');
      escribir('cantidadDatos', '3');
      escribirEnPunto(0, 'valor', '-20');
      escribirEnPunto(0, 'unidad', '°C');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

      expect(alta.request.body.puntosVerificacion).toEqual([{ valor: -20, unidad: '°C' }]);
      alta.flush({ id: 't9' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('sin puntos no llega al servidor, y dice por que hacen falta', async () => {
      rellenarObligatorios();
      elegirModalidad('PATRON_CONSTANTE');
      escribir('cantidadDatos', '3');
      // El punto que abre en blanco se queda sin rellenar.
      await enviar();

      http.expectNone({ method: 'POST', url: URL_TIPOS });
      expect(texto()).toContain('valor y su unidad');
    });

    it('el mismo punto dos veces se avisa sin esperar a guardar', async () => {
      elegirModalidad('PATRON_CONSTANTE');
      escribir('cantidadDatos', '3');
      escribirEnPunto(0, 'valor', '100');
      escribirEnPunto(0, 'unidad', 'mmHg');
      raiz()
        .querySelectorAll('button')
        .forEach((boton) => {
          if ((boton.textContent ?? '').includes('Añadir un punto')) {
            boton.click();
          }
        });
      fixture.detectChanges();
      escribirEnPunto(1, 'valor', '100');
      escribirEnPunto(1, 'unidad', 'mmHg');

      expect(texto()).toContain('declarado dos veces');
    });

    it('cambiar a la modalidad variable borra lo que ya no significa nada', () => {
      elegirModalidad('PATRON_CONSTANTE');
      escribir('cantidadDatos', '3');
      escribirEnPunto(0, 'valor', '100');
      escribirEnPunto(0, 'unidad', 'mmHg');

      elegirModalidad('PATRON_EQUIPO_VARIABLE');
      elegirModalidad('PATRON_CONSTANTE');

      expect(raiz().querySelector<HTMLInputElement>('#cantidadDatos')!.value).toBe('');
      expect(raiz().querySelector<HTMLInputElement>('#punto-valor-0')!.value).toBe('');
    });
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
