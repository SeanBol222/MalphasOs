import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { ListaClientes } from './lista-clientes';

const URL = 'http://localhost:8081/v1/api/clients';

describe('Listado de clientes', () => {
  let fixture: ComponentFixture<ListaClientes>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...proveerApiSimulado(), provideRouter([])],
    });
    fixture = TestBed.createComponent(ListaClientes);
    http = TestBed.inject(HttpTestingController);
  });

  async function responder(cuerpo: object, opciones?: { status: number; statusText: string }) {
    fixture.detectChanges();
    const peticion = http.expectOne(URL);
    if (opciones) {
      peticion.flush(cuerpo, opciones);
    } else {
      peticion.flush(cuerpo);
    }
    // La consulta resuelve por promesa, de modo que no basta con detectar cambios: hay que dejar
    // pasar los tics hasta que la senal llegue al DOM. Se espera a que el estado de carga
    // desaparezca en vez de a un numero fijo de tics, que seria una carrera.
    await esperarA(() => !texto().includes('Cargando'));
  }

  async function esperarA(condicion: () => boolean): Promise<void> {
    for (let intento = 0; intento < 20; intento += 1) {
      await fixture.whenStable();
      fixture.detectChanges();
      if (condicion()) {
        return;
      }
      await new Promise((seguir) => setTimeout(seguir, 0));
    }

    throw new Error('La pantalla no salio del estado de carga');
  }

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('mientras carga lo dice, y lo dice a un lector de pantalla', async () => {
    fixture.detectChanges();
    http.expectOne(URL);

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="status"]')).toBeTruthy();
    expect(texto()).toContain('Cargando');
  });

  it('pinta una fila por cliente, con su tipo de documento en palabras', async () => {
    await responder([
      { id: '1', razonSocial: 'Hospital Central', documento: '900123456',
        tipoIdentificacion: 'NIT_JURIDICO', estadoActivo: true },
    ]);

    expect(texto()).toContain('Hospital Central');
    expect(texto()).toContain('900123456');
    // El contrato da el codigo; la etiqueta la pone el frontend.
    expect(texto()).toContain('NIT jurídico');
  });

  it('el estado se distingue por peso tipografico y no por un color nuevo', async () => {
    // Es la regla del manual de marca que mas se contradice por reflejo: un cliente retirado NO
    // se pinta de rojo.
    await responder([
      { id: '1', razonSocial: 'Activo', estadoActivo: true },
      { id: '2', razonSocial: 'Retirado', estadoActivo: false },
    ]);

    const celdas = [...(fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr td:last-child')];

    expect(celdas[0].className).toContain('font-semibold');
    expect(celdas[1].className).not.toContain('font-semibold');
    expect(celdas.every((c) => !c.className.includes('text-accent'))).toBe(true);
  });

  it('sin clientes no ensena una tabla vacia, sino que dice por donde empezar', async () => {
    await responder([]);

    expect((fixture.nativeElement as HTMLElement).querySelector('table')).toBeNull();
    expect(texto()).toContain('Empiece dando de alta el primero');
  });

  it('un fallo del API se ensena traducido, con el codigo real del backend', async () => {
    await responder(
      { code: 'ERR_CLIENT_005', message: 'Invalid client data', details: [] },
      { status: 400, statusText: 'Bad Request' },
    );

    const aviso = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Revise los datos del formulario');
    expect(aviso?.textContent).not.toContain('Invalid');
  });

  afterEach(() => http.verify());
});
