import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import {
  ID_MMHG,
  ID_PRESION,
  ID_TIPO,
  MAGNITUDES,
  TIPOS,
  UNIDADES,
  URL_MAGNITUDES,
  URL_TIPOS,
  URL_UNIDADES_DE,
} from '../../../../testing/catalogo';
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

  async function abrir(datos: { tipos?: object } = {}): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_TIPOS, datos.tipos ?? TIPOS);
  }

  function pulsar(etiqueta: string): void {
    const boton = [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === etiqueta,
    )!;
    boton.click();
    fixture.detectChanges();
  }

  it('ensena que se le mide y en que unidad, no «la» modalidad', async () => {
    // Un termohigrometro tiene dos modalidades: una sola etiqueta tendria que mentir sobre una.
    await abrir();

    expect(texto()).toContain('Presión (mmHg)');
    expect(texto()).not.toContain('PATRON_CONSTANTE');
  });

  it('declarar las verificaciones usa su ruta propia, y manda la lista entera', async () => {
    // El backend le dio ruta aparte porque decide como se verifica el equipo. Si esto se fuera al
    // PATCH general, el cambio se perderia en silencio: ese cuerpo no admite el campo.
    await abrir();
    pulsar('Verificación');
    // El bloque se crea en este ciclo y su efecto -el que lo abre con lo ya guardado- corre en el
    // siguiente: sin dejar pasar un tic, se leeria el desplegable antes de que nadie lo rellene.
    await asentar(fixture);
    await responderA(fixture, http, URL_MAGNITUDES, MAGNITUDES);

    for (const magnitud of Object.keys(UNIDADES)) {
      for (const peticion of http.match(URL_UNIDADES_DE(magnitud))) {
        peticion.flush(UNIDADES[magnitud]);
      }
    }

    await asentar(fixture);

    // Llega con lo que el tipo ya tiene puesto: reconfigurar no empieza de cero.
    expect(raiz().querySelector<HTMLSelectElement>('#magnitud-1')!.value).toBe(ID_PRESION);
    expect(raiz().querySelector<HTMLSelectElement>('#unidad-1')!.value).toBe(ID_MMHG);
    expect(raiz().querySelector<HTMLSelectElement>('#modalidad-1')!.value).toBe('PATRON_CONSTANTE');
    expect(raiz().querySelector<HTMLInputElement>('#cantidad-1')!.value).toBe('3');
    expect(raiz().querySelector<HTMLInputElement>('#valor-1-0')!.value).toBe('100');

    const cantidad = raiz().querySelector<HTMLInputElement>('#cantidad-1')!;
    cantidad.value = '5';
    cantidad.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Guardar');
    await asentar(fixture);

    const cambio = http.expectOne({
      method: 'PATCH',
      url: `${URL_TIPOS}/${ID_TIPO}/verifications`,
    });

    expect(cambio.request.body).toEqual({
      verificaciones: [
        {
          magnitudId: ID_PRESION,
          unidadId: ID_MMHG,
          modalidad: 'PATRON_CONSTANTE',
          cantidadDatos: 5,
          puntos: [{ valor: 100 }],
        },
      ],
    });
    cambio.flush({ id: ID_TIPO });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL_TIPOS, TIPOS);
  });

  it('un tipo sin verificaciones lo dice, en vez de dejar el hueco en blanco', async () => {
    // Antes se decia con «Sin definir» a partir de una modalidad nula. Ahora es la ausencia de filas.
    await abrir({ tipos: [{ ...TIPOS[0], verificaciones: [], verificable: false }] });

    expect(texto()).toContain('Sin verificación');
  });

  it('el alta y la edicion tienen pagina propia: la ficha tecnica no cabe en una linea', async () => {
    await abrir();

    const enlaces = [...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'));

    expect(enlaces).toContain('/catalogo/tipos/nuevo');
    expect(enlaces).toContain(`/catalogo/tipos/${ID_TIPO}/editar`);
  });

  afterEach(() => http.verify());
});
