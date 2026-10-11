import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import { PROTOCOLOS, URL_PROTOCOLOS } from '../../../../testing/catalogo';
import { Protocolos } from './protocolos';

/** El catalogo de protocolos de mantenimiento, del 2026-10-10. */
describe('Protocolos del catálogo', () => {
  let fixture: ComponentFixture<Protocolos>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = ['equipment.read', 'equipment.write']): void {
    TestBed.configureTestingModule({
      providers: [...proveerApiSimulado(), ...proveerSesionFalsa({ autoridades })],
    });
    fixture = TestBed.createComponent(Protocolos);
    http = TestBed.inject(HttpTestingController);
  }

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const filas = () => [...raiz().querySelectorAll('tbody tr')];

  async function abrir(protocolos: object = PROTOCOLOS): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_PROTOCOLOS, protocolos);
  }

  async function escribir(id: string, valor: string): Promise<void> {
    const campo = raiz().querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    await asentar(fixture);
  }

  async function pulsar(etiqueta: string): Promise<void> {
    const boton = [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === etiqueta,
    );

    if (!boton) {
      throw new Error(`No hay ningun boton que diga "${etiqueta}"`);
    }

    boton.click();
    await asentar(fixture);
  }

  afterEach(() => http.verify());

  it('lista los vigentes primero, y el estado se distingue por peso y no por color', async () => {
    montar();
    await abrir([PROTOCOLOS[2], PROTOCOLOS[0], PROTOCOLOS[1]]);

    expect(filas()[2]?.textContent).toContain('Protocolo viejo');
    expect(filas()[2]?.textContent).toContain('Retirado');
    const estados = filas().map((fila) => fila.querySelectorAll('td')[2]!);
    expect(estados[0]!.className).toContain('font-semibold');
    expect(estados[2]!.className).not.toContain('font-semibold');
    expect(estados.every((celda) => !celda.className.includes('accent'))).toBe(true);
  });

  it('el buscador filtra por nombre o descripción, sin tildes', async () => {
    montar();
    await abrir();
    await escribir('filtroProtocolos', 'multimetro');

    expect(filas()).toHaveLength(1);
    expect(filas()[0]?.textContent).toContain('Verificación eléctrica');
  });

  it('crear avisa de los parecidos y manda nombre y descripción', async () => {
    montar();
    await abrir();
    await escribir('protocoloNombre', 'Limpieza profunda');

    expect(texto()).toContain('Ya existen protocolos parecidos');

    await escribir('protocoloDescripcion', ' Con cepillo. ');
    await pulsar('Crear protocolo');

    const alta = http.expectOne({ method: 'POST', url: URL_PROTOCOLOS });
    expect(alta.request.body).toEqual({ nombre: 'Limpieza profunda', descripcion: 'Con cepillo.' });
    alta.flush({
      id: 'pr9',
      nombre: 'Limpieza profunda',
      descripcion: 'Con cepillo.',
      estadoActivo: true,
    });
    // La recarga del catalogo sale un tic despues de la escritura.
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL_PROTOCOLOS, PROTOCOLOS);
  });

  it('uno retirado no cuenta como parecido ni como repetido: su nombre está libre', async () => {
    // El indice unico del servidor es solo entre vigentes. Avisar de un retirado empujaria a usar algo
    // que ya no se puede asignar.
    montar();
    await abrir();
    await escribir('protocoloNombre', 'Protocolo viejo');

    expect(texto()).not.toContain('Ya existe');
    expect(texto()).not.toContain('parecidos');
  });

  it('con el mismo nombre que uno vigente no deja crearlo', async () => {
    // Sin tildes ni mayusculas es el mismo, que es lo que el servidor rechazaria con un 409.
    montar();
    await abrir();
    await escribir('protocoloNombre', 'LIMPIEZA Y DESINFECCION');

    expect(texto()).toContain('Ya existe «Limpieza y desinfección»');
    const crear = [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Crear protocolo',
    );
    expect(crear!.disabled).toBe(true);
  });

  it('editar manda los dos campos por PATCH', async () => {
    montar();
    await abrir();
    const editar = filas()[0]!.querySelector('button') as HTMLButtonElement;
    editar.click();
    await asentar(fixture);
    await escribir('descripcion-pr1', 'Paño con alcohol.');
    await pulsar('Guardar');

    const cambio = http.expectOne({ method: 'PATCH', url: `${URL_PROTOCOLOS}/pr1` });
    expect(cambio.request.body).toEqual({
      nombre: 'Limpieza y desinfección',
      descripcion: 'Paño con alcohol.',
    });
    cambio.flush(PROTOCOLOS[0]);
    // La recarga del catalogo sale un tic despues de la escritura.
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL_PROTOCOLOS, PROTOCOLOS);
  });

  it('retirar pide confirmación, y si lo usan tipos vigentes dice el motivo del servidor', async () => {
    montar();
    await abrir();
    const retirar = [...filas()[0]!.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Retirar',
    )!;
    retirar.click();
    await asentar(fixture);

    expect(http.match({ method: 'DELETE' })).toHaveLength(0);

    await pulsar('Confirmar retiro');
    http
      .expectOne({ method: 'DELETE', url: `${URL_PROTOCOLOS}/pr1` })
      .flush(
        { code: 'ERR_EQUIPMENT_016', details: ['asignado a 2 tipos de equipo'] },
        { status: 409, statusText: 'Conflict' },
      );
    await asentar(fixture);

    expect(texto()).toContain('Quítelo de ellos antes de retirarlo');
  });

  it('sin equipment.write se lee y no se ofrece ninguna escritura', async () => {
    montar(['equipment.read']);
    await abrir();

    expect(raiz().querySelector('#protocoloNombre')).toBeNull();
    expect([...raiz().querySelectorAll('tbody button')]).toHaveLength(0);
  });
});
