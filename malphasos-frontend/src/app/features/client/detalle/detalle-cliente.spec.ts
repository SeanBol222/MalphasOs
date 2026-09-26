import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { DetalleCliente } from './detalle-cliente';

const ID = '11111111-1111-1111-1111-111111111111';
const URL = `http://localhost:8081/v1/api/clients/${ID}`;
const URL_PAISES = 'http://localhost:8081/v1/api/countries';
const URL_SEDES = `${URL}/headquarters`;

/** Un identificador de verdad, no un alias corto: es lo que el usuario NO debe ver en pantalla. */
const ID_PAIS = '22222222-2222-2222-2222-222222222222';

const CLIENTE = {
  id: ID,
  razonSocial: 'Hospital Central',
  documento: '900123456',
  tipoIdentificacion: 'NIT_JURIDICO',
  idPais: ID_PAIS,
  estadoActivo: true,
  correos: [{ id: 'c1', valor: 'contacto@hospital.co', estadoActivo: true }],
  telefonos: [{ id: 't1', valor: '6011234567', estadoActivo: true }],
  representantes: ['p1', 'p2'],
};

const PAISES = [{ id: ID_PAIS, nombre: 'Colombia', codigoIso: 'CO', estadoActivo: true }];

const SEDES = [
  { id: 's1', nombre: 'Sede Norte', idCliente: ID, estadoActivo: true },
  { id: 's2', nombre: 'Sede Vieja', idCliente: ID, estadoActivo: false },
];

@Component({ selector: 'app-listado-falso', template: '' })
class ListadoFalso {}

describe('Ficha de un cliente', () => {
  let fixture: ComponentFixture<DetalleCliente>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'clientes', component: ListadoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(DetalleCliente);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  /**
   * Deja pasar tics hasta que las senales lleguen al DOM.
   *
   * <p><b>Sin {@code whenStable()} a proposito.</b> La aplicacion es zoneless y una peticion HTTP sin
   * responder cuenta como tarea pendiente, de modo que esperar la estabilidad mientras hay una en
   * vuelo no termina nunca: la prueba agota su tiempo en vez de fallar diciendo algo. Un tic vacio
   * basta para que las promesas de la consulta se resuelvan.
   */
  async function asentar(): Promise<void> {
    for (let i = 0; i < 20; i += 1) {
      fixture.detectChanges();
      await new Promise((seguir) => setTimeout(seguir, 0));
    }
    fixture.detectChanges();
  }

  /** Abre la pantalla y responde a sus dos consultas: el cliente y el catálogo de países. */
  async function abrir(
    cliente: object = CLIENTE,
    opciones?: { status: number; statusText: string },
  ): Promise<void> {
    fixture.detectChanges();
    await responder(URL, cliente, opciones);
    await responder(URL_PAISES, PAISES);
    await responder(URL_SEDES, SEDES);
  }

  async function responder(
    url: string,
    cuerpo: object,
    opciones?: { status: number; statusText: string },
  ): Promise<void> {
    for (let intento = 0; intento < 20; intento += 1) {
      fixture.detectChanges();
      const pendientes = http.match(url);
      if (pendientes.length) {
        if (opciones) {
          pendientes[0].flush(cuerpo, opciones);
        } else {
          pendientes[0].flush(cuerpo);
        }
        await asentar();

        return;
      }
      await new Promise((seguir) => setTimeout(seguir, 0));
    }

    throw new Error(`La pantalla no pidio ${url}`);
  }

  function pulsar(etiqueta: string): void {
    const boton = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes(etiqueta),
    );

    if (!boton) {
      throw new Error(`No hay ningun boton que diga "${etiqueta}"`);
    }

    boton.click();
    fixture.detectChanges();
  }

  it('mientras carga lo dice, y lo dice a un lector de pantalla', () => {
    fixture.detectChanges();
    http.expectOne(URL);
    http.match(URL_PAISES).forEach((p) => p.flush(PAISES));
    http.match(URL_SEDES).forEach((p) => p.flush(SEDES));

    expect(raiz().querySelector('[role="status"]')).toBeTruthy();
  });

  it('ensena los datos del cliente, con el tipo de documento y el pais en palabras', async () => {
    await abrir();

    expect(texto()).toContain('Hospital Central');
    expect(texto()).toContain('900123456');
    // El contrato da codigos —NIT_JURIDICO, un UUID de pais—; las palabras las pone el frontend.
    expect(texto()).toContain('NIT jurídico');
    expect(texto()).toContain('Colombia');
    // Y el identificador no se ve: un UUID en pantalla es una fuga de la base de datos.
    expect(texto()).not.toContain(ID_PAIS);
  });

  it('un cliente sin pais no ensena un identificador ni una linea vacia', async () => {
    await abrir({ ...CLIENTE, idPais: undefined });

    expect(texto()).toContain('Sin especificar');
  });

  it('si el catalogo de paises no se pudo consultar, no dice que el cliente no tiene pais', async () => {
    // El grupo clients no tiene location.read: para esos usuarios el catalogo responde 403. Decir
    // «sin especificar» seria afirmar algo falso sobre el cliente.
    fixture.detectChanges();
    await responder(URL, CLIENTE);
    await responder(URL_PAISES, { code: 'ERR_LOCATION_001' }, { status: 403, statusText: 'Forbidden' });
    await responder(URL_SEDES, SEDES);

    expect(texto()).toContain('No disponible');
    expect(texto()).not.toContain('Sin especificar');
  });

  it('ensena sus correos y telefonos', async () => {
    await abrir();

    expect(texto()).toContain('contacto@hospital.co');
    expect(texto()).toContain('6011234567');
  });

  it('anadir un correo manda el cuerpo que el contrato declara y refresca la ficha', async () => {
    await abrir();

    const campo = raiz().querySelector<HTMLInputElement>('#correoNuevo')!;
    campo.value = 'nuevo@hospital.co';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Añadir');
    await asentar();

    const alta = http.expectOne({ method: 'POST', url: `${URL}/emails` });

    expect(alta.request.body).toEqual({ valor: 'nuevo@hospital.co' });
    alta.flush(CLIENTE);
    await asentar();

    // La segunda consulta es la que demuestra que la ficha se invalido: sin ella, el correo recien
    // anadido no apareceria hasta recargar la pagina.
    http.expectOne({ method: 'GET', url: URL }).flush(CLIENTE);
    await asentar();
    // La invalidacion alcanza tambien a las sedes, porque su clave cuelga de la del cliente.
    http.match(URL_SEDES).forEach((p) => p.flush(SEDES));
    await asentar();
  });

  it('un correo mal escrito no llega al servidor', async () => {
    await abrir();

    const campo = raiz().querySelector<HTMLInputElement>('#correoNuevo')!;
    campo.value = 'no-es-un-correo';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Añadir');
    await asentar();

    http.expectNone({ method: 'POST', url: `${URL}/emails` });
    expect(texto()).toContain('nombre@dominio');
  });

  it('quitar un telefono llama al sub-recurso con su identificador', async () => {
    await abrir();

    // El ultimo "Quitar" es el del telefono; el primero, el del correo.
    const quitar = [...raiz().querySelectorAll('button')].filter((b) =>
      (b.textContent ?? '').includes('Quitar'),
    );
    quitar[quitar.length - 1].click();
    await asentar();

    http.expectOne({ method: 'DELETE', url: `${URL}/phones/t1` }).flush(null);
    await asentar();
    http.expectOne({ method: 'GET', url: URL }).flush(CLIENTE);
    http.match(URL_SEDES).forEach((p) => p.flush(SEDES));
    await asentar();
  });

  describe('Retirar no es un clic', () => {
    it('el primer clic pide confirmacion y no manda nada', async () => {
      await abrir();
      pulsar('Retirar');

      http.expectNone({ method: 'DELETE', url: URL });
      // Y dice lo que va a pasar de verdad: el cliente no se borra.
      expect(texto()).toContain('No se borra nada');
    });

    it('confirmar retira y vuelve al listado', async () => {
      await abrir();
      pulsar('Retirar');
      pulsar('Confirmar retiro');
      await asentar();

      http.expectOne({ method: 'DELETE', url: URL }).flush(null);
      await asentar();
      http.match(URL).forEach((p) => p.flush(CLIENTE));
      http.match(URL_SEDES).forEach((p) => p.flush(SEDES));
      await asentar();

      expect(TestBed.inject(Router).url).toBe('/clientes');
    });

    it('un cliente ya retirado no ofrece retirarse otra vez', async () => {
      await abrir({ ...CLIENTE, estadoActivo: false });

      expect([...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim())).not.toContain(
        'Retirar',
      );
    });
  });

  describe('Sus sedes', () => {
    it('las lista, cada una enlazada a su ficha', async () => {
      await abrir();

      const enlaces = [...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'));

      expect(texto()).toContain('Sede Norte');
      expect(enlaces).toContain('/sedes/s1');
    });

    it('el estado de una sede tambien se distingue por peso, no por color', async () => {
      await abrir();

      const estados = [...raiz().querySelectorAll('section ul li span')];
      const abierta = estados.find((e) => (e.textContent ?? '').includes('Abierta'))!;
      const cerrada = estados.find((e) => (e.textContent ?? '').includes('Cerrada'))!;

      expect(abierta.className).toContain('font-semibold');
      expect(cerrada.className).not.toContain('font-semibold');
      expect(cerrada.className).not.toContain('text-accent');
    });

    it('sin sedes dice por donde empezar y para que sirven', async () => {
      fixture.detectChanges();
      await responder(URL, CLIENTE);
      await responder(URL_PAISES, PAISES);
      await responder(URL_SEDES, []);

      expect(texto()).toContain('Abra la primera');
    });
  });

  it('un fallo del API se ensena traducido, con el codigo real del backend', async () => {
    await abrir(
      { code: 'ERR_CLIENT_001', message: 'Client not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Ese cliente no existe');
    expect(aviso?.textContent).not.toContain('not found');
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('los dos campos de contacto tienen etiqueta asociada', async () => {
      await abrir();

      for (const id of ['correoNuevo', 'telefonoNuevo']) {
        expect(raiz().querySelector(`label[for="${id}"]`)).toBeTruthy();
      }
    });

    it('los controles respetan el area tactil minima', async () => {
      await abrir();

      for (const control of raiz().querySelectorAll('input, select, button')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });

    it('el estado se distingue por peso tipografico y no por un color nuevo', async () => {
      await abrir({ ...CLIENTE, estadoActivo: false });

      const estado = [...raiz().querySelectorAll('p')].find((p) =>
        (p.textContent ?? '').includes('Retirado'),
      )!;

      expect(estado.className).not.toContain('font-semibold');
      expect(estado.className).not.toContain('text-accent');
    });
  });

  afterEach(() => http.verify());
});
