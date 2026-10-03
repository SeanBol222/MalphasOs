import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, elegirEnBuscador, responderA } from '../../../../testing/pantalla';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import {
  ID_CELSIUS,
  ID_MMHG,
  ID_PRESION,
  ID_TEMPERATURA,
  MAGNITUDES,
  TIPOS,
  UNIDADES,
  URL_MAGNITUDES,
  URL_TIPOS,
  URL_UNIDADES_DE,
} from '../../../../testing/catalogo';
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
    // Y el bloque de verificacion pide el catalogo metrologico al montarse, aunque todavia no haya
    // ningun panel: son datos de referencia que no cambian, se piden una vez y se quedan en cache, de
    // modo que el panel que se abra despues ya los tiene. Hay que responderlo en TODA prueba de esta
    // pantalla o verify() lanza en afterEach.
    await responderA(fixture, http, URL_MAGNITUDES, MAGNITUDES);
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
    await responderA(fixture, http, URL_MAGNITUDES, MAGNITUDES);
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

  describe('Que se le verifica', () => {
    /**
     * Despliega un panel y le pone magnitud y unidad.
     *
     * <p>Las unidades se consultan al elegir la magnitud, así que hay que responderlas: no están
     * cargadas de antemano porque dependen de lo elegido.
     */
    async function abrirPanel(magnitudId: string, unidadId: string): Promise<void> {
      escribirEn('#cuantasVerificaciones', '1');
      await asentar(fixture);
      escribirEn('#magnitud-1', magnitudId);
      await responderAUnidades();
      escribirEn('#unidad-1', unidadId);
    }

    async function responderAUnidades(): Promise<void> {
      for (const magnitud of Object.keys(UNIDADES)) {
        for (const peticion of http.match(URL_UNIDADES_DE(magnitud))) {
          peticion.flush(UNIDADES[magnitud]);
        }
      }

      await asentar(fixture);
    }

    function escribirEn(selector: string, valor: string): void {
      const control = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
      control.value = valor;
      control.dispatchEvent(new Event(control.tagName === 'SELECT' ? 'change' : 'input'));
      fixture.detectChanges();
    }

    it('el contador despliega un panel por cada cosa que se le mida', async () => {
      // Sin panel no hay nada: cero verificaciones significa que al tipo no se le verifica nada.
      expect(raiz().querySelector('#magnitud-1')).toBeNull();

      escribirEn('#cuantasVerificaciones', '2');
      await asentar(fixture);

      expect(raiz().querySelector('#magnitud-1')).toBeTruthy();
      expect(raiz().querySelector('#magnitud-2')).toBeTruthy();
      await responderAUnidades();
    });

    it('una modalidad constante pide cuantas lecturas y en que valores', async () => {
      await abrirPanel(ID_PRESION, ID_MMHG);

      // Con patron y equipo variables no hay nada constante que declarar, asi que los campos no estan.
      expect(raiz().querySelector('#cantidad-1')).toBeNull();

      escribirEn('#modalidad-1', 'PATRON_EQUIPO_VARIABLE');

      expect(raiz().querySelector('#cantidad-1')).toBeNull();

      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');

      expect(raiz().querySelector('#cantidad-1')).toBeTruthy();
      expect(raiz().querySelector('#cuantos-1')).toBeTruthy();
      // Y abre con un punto en blanco, porque hace falta al menos uno.
      expect(raiz().querySelector('#valor-1-0')).toBeTruthy();
    });

    it('dice que las lecturas son por punto, no en total', async () => {
      // Confundirlo daria un reporte con un tercio de los datos que hacian falta.
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');

      expect(texto()).toContain('En cada punto se toman estas lecturas');
    });

    it('manda la verificacion entera: magnitud, unidad, modalidad, cantidad y puntos', async () => {
      rellenarObligatorios();
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '5');
      escribirEn('#valor-1-0', '100');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

      expect(alta.request.body.verificaciones).toEqual([
        {
          magnitudId: ID_PRESION,
          unidadId: ID_MMHG,
          modalidad: 'PATRON_CONSTANTE',
          cantidadDatos: 5,
          // Solo el valor: la unidad la declara la verificacion, una sola vez.
          puntos: [{ valor: 100 }],
        },
      ]);
      alta.flush({ id: 't9' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('un punto negativo vale: un congelador se verifica a -20 grados', async () => {
      rellenarObligatorios();
      await abrirPanel(ID_TEMPERATURA, ID_CELSIUS);
      escribirEn('#modalidad-1', 'EQUIPO_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      escribirEn('#valor-1-0', '-20');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

      expect(alta.request.body.verificaciones[0].puntos).toEqual([{ valor: -20 }]);
      alta.flush({ id: 't9' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('sin puntos no llega al servidor, y dice por que hacen falta', async () => {
      rellenarObligatorios();
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      // El punto que abre en blanco se queda sin rellenar.
      await enviar();

      http.expectNone({ method: 'POST', url: URL_TIPOS });
      expect(texto()).toContain('al menos un punto');
    });

    it('sin magnitud no llega al servidor', async () => {
      // El panel esta abierto pero vacio: el servidor lo rechazaria con un 400 que nadie sabria leer.
      rellenarObligatorios();
      escribirEn('#cuantasVerificaciones', '1');
      await asentar(fixture);
      await enviar();

      http.expectNone({ method: 'POST', url: URL_TIPOS });
      expect(texto()).toContain('Indique qué se mide');
      await responderAUnidades();
    });

    it('el mismo punto dos veces se avisa sin esperar a guardar', async () => {
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      escribirEn('#cuantos-1', '2');
      escribirEn('#valor-1-0', '100');
      escribirEn('#valor-1-1', '100');

      expect(texto()).toContain('declarado dos veces');
    });

    it('cambiar a la modalidad variable borra lo que ya no significa nada', async () => {
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      escribirEn('#valor-1-0', '100');

      escribirEn('#modalidad-1', 'PATRON_EQUIPO_VARIABLE');
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');

      expect(raiz().querySelector<HTMLInputElement>('#cantidad-1')!.value).toBe('');
      expect(raiz().querySelector<HTMLInputElement>('#valor-1-0')!.value).toBe('');
    });

    it('la unidad solo ofrece las de la magnitud elegida', async () => {
      // El esquema ata el par (magnitud, unidad) con una foranea compuesta: ofrecer °C para presion
      // seria ofrecer un 409.
      await abrirPanel(ID_PRESION, ID_MMHG);

      const unidades = [...raiz().querySelectorAll('#unidad-1 option')]
        .map((o) => o.textContent?.trim())
        .join(' ');

      expect(unidades).toContain('mmHg');
      expect(unidades).not.toContain('°C');
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
    // El try/finally no es adorno: si verify() lanza por una peticion abierta, sin el no se
    // desinstalaria el doble de localStorage y el fichero SIGUIENTE heredaria lo que este guardo. Es
    // la variante con contaminacion del fallo que este proyecto ya pago una vez -- un fallo en
    // afterEach se lee como ochenta y seis -- y asi el rojo se queda donde ocurrio.
    try {
      http.verify();
    } finally {
      desinstalarAlmacenamiento();
    }
  });
});
