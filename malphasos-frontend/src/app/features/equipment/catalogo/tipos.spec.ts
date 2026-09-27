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

  it('cambiar la modalidad usa su ruta propia, y lleva la cantidad y los puntos', async () => {
    // El backend le dio ruta aparte porque decide como se verifica el equipo. Si esto se fuera al
    // PATCH general, el cambio se perderia en silencio: ese cuerpo no admite los campos.
    await abrir();
    pulsar('Modalidad');
    // El bloque se crea en este ciclo y su efecto -el que lo abre con lo ya guardado- corre en el
    // siguiente: sin dejar pasar un tic, se leeria el desplegable antes de que nadie lo rellene.
    await asentar(fixture);

    const selector = raiz().querySelector<HTMLSelectElement>('#modalidadVerificacion')!;

    // Llega con lo que el tipo ya tiene puesto: reconfigurar no empieza de cero.
    expect(selector.value).toBe('PATRON_CONSTANTE');
    expect(raiz().querySelector<HTMLInputElement>('#cantidadDatos')!.value).toBe('3');
    expect(raiz().querySelector<HTMLInputElement>('#punto-valor-0')!.value).toBe('100');

    const cantidad = raiz().querySelector<HTMLInputElement>('#cantidadDatos')!;
    cantidad.value = '5';
    cantidad.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Guardar');
    await asentar(fixture);

    const cambio = http.expectOne({
      method: 'PATCH',
      url: `${URL_TIPOS}/${ID_TIPO}/verification-mode`,
    });

    expect(cambio.request.body).toEqual({
      modalidad: 'PATRON_CONSTANTE',
      cantidadDatos: 5,
      puntosVerificacion: [{ valor: 100, unidad: 'mmHg' }],
    });
    cambio.flush({ id: ID_TIPO });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL_TIPOS, TIPOS);
  });

  it('el resumen de la fila dice cuantas lecturas por punto y cuantos puntos hay', async () => {
    // Es lo que permite ver de un golpe si un tipo esta configurado para llenar un reporte.
    await abrir();

    expect(texto()).toContain('3 lecturas por punto');
    expect(texto()).toContain('1 punto');
  });

  it('el alta y la edicion tienen pagina propia: la ficha tecnica no cabe en una linea', async () => {
    await abrir();

    const enlaces = [...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'));

    expect(enlaces).toContain('/catalogo/tipos/nuevo');
    expect(enlaces).toContain(`/catalogo/tipos/${ID_TIPO}/editar`);
  });

  afterEach(() => http.verify());
});
