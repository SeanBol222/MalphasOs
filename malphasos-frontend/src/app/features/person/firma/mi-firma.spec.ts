import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import { MiFirma } from './mi-firma';

const URL = 'http://localhost:8081/v1/api/persons/me/signature';
const PNG = 'data:image/png;base64,iVBORw0KGgo=';

/** La firma de quien tiene la sesión (V38, 2026-10-17). */
describe('Mi firma', () => {
  let fixture: ComponentFixture<MiFirma>;
  let http: HttpTestingController;
  const original = HTMLCanvasElement.prototype.toDataURL;

  beforeEach(() => {
    // El entorno de pruebas no pinta: ver el spec del lienzo.
    HTMLCanvasElement.prototype.toDataURL = () => PNG;
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['report.write'] }),
      ],
    });
    fixture = TestBed.createComponent(MiFirma);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    HTMLCanvasElement.prototype.toDataURL = original;
    http.verify();
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const guardar = () =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Guardar firma')!;
  const puntero = (tipo: string) =>
    raiz()
      .querySelector('canvas')!
      .dispatchEvent(
        Object.assign(new MouseEvent(tipo, { clientX: 5, clientY: 5, bubbles: true }), {
          pointerId: 1,
        }),
      );

  async function abrir(firma: object | null): Promise<void> {
    fixture.detectChanges();
    if (firma) {
      await responderA(fixture, http, URL, firma);
    } else {
      await responderA(
        fixture,
        http,
        URL,
        { code: 'ERR_PERSON_003', message: 'The person has not saved a signature' },
        { status: 404, statusText: 'Not Found' },
      );
    }
  }

  it('enseña la firma guardada', async () => {
    await abrir({ imagen: PNG, actualizada: '2026-10-17T09:00:00' });

    expect(raiz().querySelector<HTMLImageElement>('[data-firma-actual]')!.src).toBe(PNG);
  });

  it('sin firma lo dice, sin tratarlo como un error', async () => {
    await abrir(null);

    expect(raiz().querySelector('[data-sin-firma]')).not.toBeNull();
    expect(raiz().querySelector('[role="alert"]')).toBeNull();
  });

  it('guardar sin dibujar no llega al servidor y pide la firma (RF-18)', async () => {
    await abrir(null);
    guardar().click();
    await asentar(fixture);

    http.expectNone({ method: 'PATCH', url: URL });
    expect(raiz().textContent).toContain('Dibuje su firma antes de guardarla.');
  });

  it('el aviso se va en cuanto se dibuja: ya no falta nada', async () => {
    await abrir(null);
    guardar().click();
    await asentar(fixture);
    puntero('pointerdown');
    puntero('pointerup');
    await asentar(fixture);

    expect(raiz().textContent).not.toContain('Dibuje su firma antes de guardarla.');
  });

  it('dibujar y guardar manda el PNG, y lo dice', async () => {
    await abrir(null);
    puntero('pointerdown');
    puntero('pointerup');
    await asentar(fixture);
    guardar().click();
    await asentar(fixture);

    const guardado = http.expectOne({ method: 'PATCH', url: URL });
    expect(guardado.request.body).toEqual({ imagen: PNG });
    guardado.flush({ imagen: PNG, actualizada: '2026-10-17T09:00:00' });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL, { imagen: PNG, actualizada: '2026-10-17T09:00:00' });

    expect(raiz().textContent).toContain('Firma guardada.');
  });

  it('una cuenta sin persona —creada a mano en Keycloak— lo dice al guardar, con qué hacer', async () => {
    await abrir(null);
    puntero('pointerdown');
    puntero('pointerup');
    await asentar(fixture);
    guardar().click();
    await asentar(fixture);

    http
      .expectOne({ method: 'PATCH', url: URL })
      .flush(
        { code: 'ERR_PERSON_001', message: 'Person not found' },
        { status: 404, statusText: 'Not Found' },
      );
    await asentar(fixture);

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
      'no corresponde a ninguna persona registrada',
    );
  });
});
