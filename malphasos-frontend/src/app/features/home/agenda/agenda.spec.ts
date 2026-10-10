import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  CLIENTES,
  ID_SEDE,
  orden,
  SEDE,
  URL_CLIENTES,
  URL_ORDENES,
  urlSede,
} from '../../../../testing/ordenes';
import { HOY } from '../../../core/fechas';
import { Agenda } from './agenda';

/** La semana del martes 6 de octubre de 2026, de lunes a domingo. */
const SEMANA = `${URL_ORDENES}?desde=2026-10-05&hasta=2026-10-11`;

describe('Agenda del inicio', () => {
  let fixture: ComponentFixture<Agenda>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['work-order.read'], identificador: 'yo' }),
        provideRouter([]),
        // Un martes a las nueve. Sin fijarlo, la prueba solo pasaria el dia en que se escribio.
        { provide: HOY, useValue: () => new Date(2026, 9, 6, 9, 0) },
      ],
    });
    fixture = TestBed.createComponent(Agenda);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const boton = (texto: string) =>
    [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
    ) as HTMLButtonElement;
  const titulo = () => raiz().querySelector('[aria-live="polite"]')!.textContent!.trim();

  /** Responde el periodo con esas ordenes, y los nombres que la agenda cruza. */
  async function responder(url: string, ordenes: object[]): Promise<void> {
    await responderA(fixture, http, url, ordenes);
    http.match(URL_CLIENTES).forEach((peticion) => peticion.flush(CLIENTES));
    http.match(urlSede(ID_SEDE)).forEach((peticion) => peticion.flush(SEDE));
    await asentar(fixture);
  }

  async function pulsar(texto: string): Promise<void> {
    boton(texto).click();
    await asentar(fixture);
  }

  describe('El periodo que pide', () => {
    it('abre en la semana de hoy, de lunes a domingo, sin filtrar por ingeniero', async () => {
      fixture.detectChanges();
      await responder(SEMANA, []);

      expect(titulo()).toBe('5 al 11 de octubre de 2026');
    });

    it('una semana que cruza de mes nombra los dos meses', async () => {
      fixture.detectChanges();
      await responder(SEMANA, []);
      await pulsar('Semana anterior');
      await responder(`${URL_ORDENES}?desde=2026-09-28&hasta=2026-10-04`, []);

      expect(titulo()).toBe('28 de septiembre al 4 de octubre de 2026');
    });

    it('el mes pide semanas completas: del lunes antes del día 1 al domingo después del último', async () => {
      // Octubre de 2026 empieza en jueves y acaba en sabado.
      fixture.detectChanges();
      await responder(SEMANA, []);
      await pulsar('Mes');
      await responder(`${URL_ORDENES}?desde=2026-09-28&hasta=2026-11-01`, []);

      expect(titulo()).toBe('octubre de 2026');
      expect(raiz().querySelectorAll('li[data-dia]')).toHaveLength(35);
    });

    it('las flechas mueven el periodo y «Hoy» vuelve', async () => {
      fixture.detectChanges();
      await responder(SEMANA, []);
      await pulsar('Semana siguiente');
      await responder(`${URL_ORDENES}?desde=2026-10-12&hasta=2026-10-18`, []);
      await pulsar('Hoy');
      // La semana de hoy ya esta en cache: no se vuelve a pedir, y el titulo vuelve igual.
      await asentar(fixture);

      expect(titulo()).toBe('5 al 11 de octubre de 2026');
    });

    it('«Solo mis órdenes» pide las del ingeniero de la sesión', async () => {
      // El identificador de la sesion es el de la persona: no hace falta preguntar quien soy.
      fixture.detectChanges();
      await responder(SEMANA, []);
      raiz().querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
      await asentar(fixture);

      expect(http.match(`${SEMANA}&idIngeniero=yo`)).toHaveLength(1);
    });
  });

  describe('Lo que pinta', () => {
    const celda = (iso: string) => raiz().querySelector(`li[data-dia="${iso}"]`) as HTMLElement;

    it('cada orden cae en su día, con el cliente y la sede en palabras', async () => {
      fixture.detectChanges();
      await responder(SEMANA, [orden({ fechaMantenimiento: '2026-10-08' })]);

      expect(celda('2026-10-08').textContent).toContain('Hospital Central');
      expect(celda('2026-10-08').textContent).toContain('Sede Norte');
      expect(celda('2026-10-07').querySelector('button')).toBeNull();
    });

    it('hoy se marca, y lo dice para quien no ve la marca', async () => {
      fixture.detectChanges();
      await responder(SEMANA, []);

      expect(celda('2026-10-06').textContent).toContain('(hoy)');
      expect(celda('2026-10-05').textContent).not.toContain('(hoy)');
    });

    it('el estado se distingue por forma y no por un color nuevo', async () => {
      fixture.detectChanges();
      await responder(SEMANA, [
        orden({ id: 'a', fechaMantenimiento: '2026-10-06', estadoEjecucion: 'EN_EJECUCION' }),
        orden({ id: 'b', fechaMantenimiento: '2026-10-07' }),
        orden({ id: 'c', fechaMantenimiento: '2026-10-08', estadoEjecucion: 'EJECUTADA' }),
        orden({ id: 'd', fechaMantenimiento: '2026-10-09', estadoActivo: false }),
      ]);

      const [enEjecucion, creada, ejecutada, anulada] = [
        '2026-10-06',
        '2026-10-07',
        '2026-10-08',
        '2026-10-09',
      ].map((dia) => celda(dia).querySelector('button')!);

      expect(enEjecucion.classList).toContain('bg-ink');
      expect(creada.classList).toContain('bg-white');
      expect(ejecutada.classList).toContain('border-dashed');
      expect(anulada.classList).toContain('line-through');
      // Y lo dicen con palabras, que es lo que oye un lector de pantalla.
      expect(anulada.getAttribute('aria-label')).toContain('Anulada');
      expect(enEjecucion.getAttribute('aria-label')).toContain('En ejecución');
      for (const bloque of [enEjecucion, creada, ejecutada, anulada]) {
        expect(bloque.className).not.toContain('accent');
      }
    });

    it('elegir una orden enseña su resumen y el enlace a ella', async () => {
      fixture.detectChanges();
      await responder(SEMANA, [orden({ fechaMantenimiento: '2026-10-08', idIngeniero: 'yo' })]);

      celda('2026-10-08').querySelector('button')!.click();
      await asentar(fixture);

      const panel = raiz().querySelector('aside')!;

      expect(panel.textContent).toContain('Hospital Central');
      expect(panel.textContent).toContain('Trimestral');
      expect(panel.textContent).toContain('Usted');
      // La fecha en palabras y no en ISO, que es para la maquina.
      expect(panel.textContent).toContain('Jueves 8 de octubre');
      expect(panel.textContent).not.toContain('2026-10-08');
      expect(panel.querySelector('a')!.getAttribute('href')).toBe('/ordenes/o1');
    });

    it('sin órdenes en el periodo lo dice, en vez de un panel vacío', async () => {
      fixture.detectChanges();
      await responder(SEMANA, []);

      expect(raiz().querySelector('aside')!.textContent).toContain(
        'No hay órdenes en este periodo',
      );
    });

    it('en un teléfono los días vacíos se esconden, salvo hoy', async () => {
      fixture.detectChanges();
      await responder(SEMANA, [orden({ fechaMantenimiento: '2026-10-08' })]);

      expect(celda('2026-10-07').classList).toContain('max-md:hidden');
      expect(celda('2026-10-06').classList).not.toContain('max-md:hidden');
      expect(celda('2026-10-08').classList).not.toContain('max-md:hidden');
    });
  });
});
