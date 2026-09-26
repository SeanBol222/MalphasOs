import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import { ID_TIPO, TIPOS, URL_TIPOS } from '../../../../testing/catalogo';
import { TiposDeEquipo } from './tipos';

describe('Tipos de equipo', () => {
  let fixture: ComponentFixture<TiposDeEquipo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(TiposDeEquipo);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_TIPOS, TIPOS);
  }

  function pulsar(etiqueta: string): void {
    const boton = [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === etiqueta,
    )!;
    boton.click();
    fixture.detectChanges();
  }

  it('ensena la modalidad en palabras y no su codigo', async () => {
    await abrir();

    expect(texto()).toContain('Patrón constante');
    expect(texto()).not.toContain('PATRON_CONSTANTE');
  });

  it('cambiar la modalidad usa su ruta propia, no la edicion general', async () => {
    // El backend le dio ruta aparte porque decide como se verifica el equipo. Si esto se fuera al
    // PATCH general, el cambio se perderia en silencio: ese cuerpo no admite el campo.
    await abrir();
    pulsar('Modalidad');

    const selector = raiz().querySelector<HTMLSelectElement>(`#modalidad-${ID_TIPO}`)!;

    // Llega con la modalidad actual puesta.
    expect(selector.value).toBe('PATRON_CONSTANTE');

    selector.value = 'EQUIPO_CONSTANTE';
    selector.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    pulsar('Guardar');
    await asentar(fixture);

    const cambio = http.expectOne({
      method: 'PATCH',
      url: `${URL_TIPOS}/${ID_TIPO}/verification-mode`,
    });

    expect(cambio.request.body).toEqual({ modalidad: 'EQUIPO_CONSTANTE' });
    cambio.flush({ id: ID_TIPO });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL_TIPOS, TIPOS);
  });

  it('el alta y la edicion tienen pagina propia: la ficha tecnica no cabe en una linea', async () => {
    await abrir();

    const enlaces = [...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'));

    expect(enlaces).toContain('/catalogo/tipos/nuevo');
    expect(enlaces).toContain(`/catalogo/tipos/${ID_TIPO}/editar`);
  });

  afterEach(() => http.verify());
});
