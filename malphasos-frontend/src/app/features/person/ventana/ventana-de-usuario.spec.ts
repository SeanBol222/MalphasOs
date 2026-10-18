import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import Keycloak from 'keycloak-js';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { KeycloakFalso, proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import { VentanaDeUsuario } from './ventana-de-usuario';

const URL_YO = 'http://localhost:8081/v1/api/persons/me';
const URL_FIRMA = 'http://localhost:8081/v1/api/persons/me/signature';
const PNG = 'data:image/png;base64,iVBORw0KGgo=';

const INGENIERA = {
  identificador: 'p-sesion',
  cedula: '1000000000',
  primerNombre: 'Ada',
  primerApellido: 'Lovelace',
  tipoPersona: 'ENGINEER',
  estadoActivo: true,
  emailPersonList: [
    { correoPersona: 'vieja@ejemplo.test', estadoActivo: false },
    { correoPersona: 'otro@ejemplo.test', estadoActivo: true },
    { correoPersona: 'ada@ejemplo.test', estadoActivo: true, principal: true },
  ],
  phonePersonList: [{ telefonoPersona: '3000000000', estadoActivo: true }],
};

const SIN_FICHA = { status: 404, statusText: 'Not Found' };

/**
 * La ventana de quien tiene la sesión (2026-10-17): sus datos, su firma digital y cerrar sesión. Recoge
 * las pruebas de la página «Mi firma», que se retiró al entrar la firma aquí.
 */
describe('Ventana de la sesión', () => {
  let fixture: ComponentFixture<VentanaDeUsuario>;
  let http: HttpTestingController;
  const original = HTMLCanvasElement.prototype.toDataURL;

  function montar(autoridades: readonly string[]): void {
    // El entorno de pruebas no pinta: ver el spec del lienzo.
    HTMLCanvasElement.prototype.toDataURL = () => PNG;
    TestBed.configureTestingModule({
      providers: [...proveerApiSimulado(), ...proveerSesionFalsa({ autoridades })],
    });
    fixture = TestBed.createComponent(VentanaDeUsuario);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    HTMLCanvasElement.prototype.toDataURL = original;
    http.verify();
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const boton = (etiqueta: string) =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === etiqueta);
  const puntero = (tipo: string) =>
    raiz()
      .querySelector('canvas')!
      .dispatchEvent(
        Object.assign(new MouseEvent(tipo, { clientX: 5, clientY: 5, bubbles: true }), {
          pointerId: 1,
        }),
      );

  async function pulsar(etiqueta: string): Promise<void> {
    boton(etiqueta)!.click();
    await asentar(fixture);
  }

  /** Abre la ventana de quien cierra informes, con su ficha y la firma que diga. */
  async function abrirIngeniera(firma: object | null): Promise<void> {
    montar(['report.write']);
    fixture.detectChanges();
    await responderA(fixture, http, URL_YO, INGENIERA);
    if (firma) {
      await responderA(fixture, http, URL_FIRMA, firma);
    } else {
      await responderA(
        fixture,
        http,
        URL_FIRMA,
        { code: 'ERR_PERSON_003', message: 'The person has not saved a signature' },
        SIN_FICHA,
      );
    }
  }

  describe('Los datos', () => {
    it('enseña el nombre de la ficha, el usuario, el rol y el correo de la cuenta', async () => {
      await abrirIngeniera(null);

      expect(raiz().querySelector('h2')?.textContent?.trim()).toBe('Ada Lovelace');
      expect(texto()).toContain('sbolivar');
      expect(raiz().querySelector('[data-rol]')?.textContent?.trim()).toBe('Ingeniero');
      // El de la cuenta, aunque no sea el primero; nunca el retirado.
      expect(raiz().querySelector('[data-datos]')?.textContent).toContain('ada@ejemplo.test');
      expect(raiz().querySelector('[data-datos]')?.textContent).not.toContain('otro@ejemplo.test');
      expect(raiz().querySelector('[data-datos]')?.textContent).not.toContain('vieja@ejemplo.test');
      expect(raiz().querySelector('[data-datos]')?.textContent).toContain('1000000000');
    });

    it('no tiene un solo campo que editar: los datos se cambian en «Personas»', async () => {
      await abrirIngeniera(null);

      expect(raiz().querySelectorAll('input, select, textarea')).toHaveLength(0);
    });

    it('un representante ve sus datos y no la firma, que no necesita', async () => {
      // Un representante tiene report.read y no report.write: no cierra informes.
      montar(['report.read']);
      fixture.detectChanges();
      await responderA(fixture, http, URL_YO, { ...INGENIERA, tipoPersona: 'CEO_CLIENT' });

      expect(raiz().querySelector('[data-rol]')?.textContent?.trim()).toBe(
        'Representante del cliente',
      );
      expect(texto()).not.toContain('Firma digital');
      http.expectNone(URL_FIRMA);
    });

    it('el SuperUsuario no tiene ficha: lo dice, y no pregunta por una firma que no puede tener', async () => {
      // Su 404 en la firma se leería como «todavía no tiene firma», que no podría arreglar.
      montar(['super.admin.full', 'admin.full']);
      fixture.detectChanges();
      await responderA(
        fixture,
        http,
        URL_YO,
        { code: 'ERR_PERSON_001', message: 'Person not found' },
        SIN_FICHA,
      );

      expect(raiz().querySelector('[data-sin-ficha]')).not.toBeNull();
      expect(raiz().querySelector('[role="alert"]')).toBeNull();
      expect(raiz().querySelector('h2')?.textContent?.trim()).toBe('Sean Bolívar');
      expect(raiz().querySelector('[data-rol]')?.textContent?.trim()).toBe('Super usuario');
      http.expectNone(URL_FIRMA);
    });
  });

  describe('La firma digital', () => {
    it('enseña la firma guardada y ofrece cambiarla', async () => {
      await abrirIngeniera({ imagen: PNG, actualizada: '2026-10-17T09:00:00' });

      expect(raiz().querySelector<HTMLImageElement>('[data-firma-actual]')!.src).toBe(PNG);
      expect(boton('Cambiar firma')).toBeDefined();
      expect(raiz().querySelector('canvas')).toBeNull();
    });

    it('sin firma lo dice, sin tratarlo como un error, y ofrece dibujarla', async () => {
      await abrirIngeniera(null);

      expect(raiz().querySelector('[data-sin-firma]')).not.toBeNull();
      expect(raiz().querySelector('[role="alert"]')).toBeNull();
      expect(boton('Dibujar firma')).toBeDefined();
    });

    it('cambiarla abre el lienzo en la misma ventana, y cancelar lo cierra sin mandar nada', async () => {
      await abrirIngeniera({ imagen: PNG, actualizada: '2026-10-17T09:00:00' });
      await pulsar('Cambiar firma');

      expect(raiz().querySelector('canvas')).not.toBeNull();

      await pulsar('Cancelar');

      expect(raiz().querySelector('canvas')).toBeNull();
      http.expectNone({ method: 'PATCH', url: URL_FIRMA });
    });

    it('guardar sin dibujar no llega al servidor y pide la firma (RF-18)', async () => {
      await abrirIngeniera(null);
      await pulsar('Dibujar firma');
      await pulsar('Guardar firma');

      http.expectNone({ method: 'PATCH', url: URL_FIRMA });
      expect(texto()).toContain('Dibuje su firma antes de guardarla.');
    });

    it('el aviso se va en cuanto se dibuja: ya no falta nada', async () => {
      await abrirIngeniera(null);
      await pulsar('Dibujar firma');
      await pulsar('Guardar firma');
      puntero('pointerdown');
      puntero('pointerup');
      await asentar(fixture);

      expect(texto()).not.toContain('Dibuje su firma antes de guardarla.');
    });

    it('dibujar y guardar manda el PNG, vuelve a la firma y lo dice', async () => {
      await abrirIngeniera(null);
      await pulsar('Dibujar firma');
      puntero('pointerdown');
      puntero('pointerup');
      await asentar(fixture);
      await pulsar('Guardar firma');

      const guardado = http.expectOne({ method: 'PATCH', url: URL_FIRMA });
      expect(guardado.request.body).toEqual({ imagen: PNG });
      guardado.flush({ imagen: PNG, actualizada: '2026-10-17T09:00:00' });
      await asentar(fixture);
      await atenderRefresco(fixture, http, URL_FIRMA, {
        imagen: PNG,
        actualizada: '2026-10-17T09:00:00',
      });

      expect(texto()).toContain('Firma guardada.');
      expect(raiz().querySelector('canvas')).toBeNull();
      expect(raiz().querySelector('[data-firma-actual]')).not.toBeNull();
    });

    it('si guardar falla, el lienzo sigue abierto y el error se explica', async () => {
      await abrirIngeniera(null);
      await pulsar('Dibujar firma');
      puntero('pointerdown');
      puntero('pointerup');
      await asentar(fixture);
      await pulsar('Guardar firma');

      http
        .expectOne({ method: 'PATCH', url: URL_FIRMA })
        .flush(
          { code: 'ERR_PERSON_001', message: 'Person not found' },
          { status: 404, statusText: 'Not Found' },
        );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'no corresponde a ninguna persona registrada',
      );
      expect(raiz().querySelector('canvas')).not.toBeNull();
    });
  });

  describe('Cerrar', () => {
    it('la equis y Escape piden cerrar; quien la abrió decide', async () => {
      await abrirIngeniera(null);
      let pedidas = 0;
      fixture.componentInstance.cerrar.subscribe(() => (pedidas += 1));

      raiz().querySelector<HTMLButtonElement>('button[aria-label="Cerrar"]')!.click();
      raiz().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

      expect(pedidas).toBe(2);
    });

    it('«Volver al menú», el del teléfono, también', async () => {
      await abrirIngeniera(null);
      let pedidas = 0;
      fixture.componentInstance.cerrar.subscribe(() => (pedidas += 1));

      boton('Volver al menú')!.click();

      expect(pedidas).toBe(1);
    });

    it('cerrar sesión le pide la salida a Keycloak', async () => {
      await abrirIngeniera(null);

      boton('Cerrar sesión')!.click();

      expect((TestBed.inject(Keycloak) as KeycloakFalso).salidasPedidas).toBe(1);
    });
  });
});
