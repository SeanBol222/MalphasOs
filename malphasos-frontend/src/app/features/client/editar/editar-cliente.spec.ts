import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { EditarCliente } from './editar-cliente';

const ID = '11111111-1111-1111-1111-111111111111';
const ID_PAIS = '22222222-2222-2222-2222-222222222222';
const URL = `http://localhost:8081/v1/api/clients/${ID}`;
const URL_PAISES = 'http://localhost:8081/v1/api/countries';

const CLIENTE = {
  id: ID,
  razonSocial: 'Hospital Central',
  sigla: 'HCE',
  documento: '900123456',
  tipoIdentificacion: 'NIT_JURIDICO',
  idPais: ID_PAIS,
  estadoActivo: true,
};

const PAISES = [
  { id: ID_PAIS, nombre: 'Colombia', codigoIso: 'CO', estadoActivo: true },
  { id: '33333333-3333-3333-3333-333333333333', nombre: 'Perú', codigoIso: 'PE', estadoActivo: true },
];

@Component({ selector: 'app-ficha-falsa', template: '' })
class FichaFalsa {}

describe('Edicion de un cliente', () => {
  let fixture: ComponentFixture<EditarCliente>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'clientes/:id', component: FichaFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(EditarCliente);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function asentar(): Promise<void> {
    for (let i = 0; i < 20; i += 1) {
      fixture.detectChanges();
      await new Promise((seguir) => setTimeout(seguir, 0));
    }
    fixture.detectChanges();
  }

  async function abrir(cliente: object = CLIENTE): Promise<void> {
    fixture.detectChanges();
    await responder(URL, cliente);
    await responder(URL_PAISES, PAISES);
  }

  async function responder(url: string, cuerpo: object): Promise<void> {
    for (let intento = 0; intento < 20; intento += 1) {
      fixture.detectChanges();
      const pendientes = http.match(url);
      if (pendientes.length) {
        pendientes[0].flush(cuerpo);
        await asentar();

        return;
      }
      await new Promise((seguir) => setTimeout(seguir, 0));
    }

    throw new Error(`La pantalla no pidio ${url}`);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar();
  }

  it('llega con los datos del cliente ya puestos', async () => {
    // Un formulario de edicion vacio obligaria a teclear de nuevo lo que ya esta guardado, y quien
    // solo queria corregir una letra acabaria borrando el resto.
    await abrir();

    expect(raiz().querySelector<HTMLInputElement>('#razonSocial')!.value).toBe('Hospital Central');
    // El campo de busqueda ensena el NOMBRE y guarda el identificador: si ensenara el UUID, quien
    // edita no sabria que pais tiene puesto.
    expect(raiz().querySelector<HTMLInputElement>('#idPais')!.value).toBe('Colombia');
    expect(raiz().querySelector<HTMLInputElement>('#sigla')!.value).toBe('HCE');
  });

  it('no ofrece el documento, porque el backend no admite cambiarlo', async () => {
    await abrir();

    expect(raiz().querySelector('#documento')).toBeNull();
    // Y lo dice, en vez de dejar al usuario buscandolo.
    expect(texto()).toContain('no se editan');
  });

  it('manda un PATCH con lo que el contrato admite, y nada mas', async () => {
    await abrir();
    escribir('razonSocial', 'Hospital del Norte');
    await enviar();

    const peticion = http.expectOne({ method: 'PATCH', url: URL });

    expect(peticion.request.body).toEqual({
      razonSocial: 'Hospital del Norte',
      idPais: ID_PAIS,
    });
    peticion.flush(CLIENTE);
    await asentar();
    http.match(URL).forEach((p) => p.flush(CLIENTE));
    await asentar();
  });

  it('una sigla corregida viaja en mayusculas por su propia ruta, despues de los datos', async () => {
    // Tiene ruta propia en el backend y solo se manda si cambio: la sin cambiar no llama a nada.
    await abrir();
    escribir('sigla', 'hcn');
    await enviar();

    http.expectOne({ method: 'PATCH', url: URL }).flush(CLIENTE);
    await asentar();
    // La mutacion espera a la recarga que su invalidacion dispara: la sigla sale despues de ella.
    http.match(URL).forEach((p) => p.flush(CLIENTE));
    await asentar();
    const sigla = http.expectOne({ method: 'PATCH', url: `${URL}/acronym` });
    expect(sigla.request.body).toEqual({ sigla: 'HCN' });
    sigla.flush({ ...CLIENTE, sigla: 'HCN' });
    await asentar();
    http.match(URL).forEach((p) => p.flush(CLIENTE));
    await asentar();
  });

  it('una sigla que ya tiene otro cliente lo dice, sin salir del formulario', async () => {
    await abrir();
    escribir('sigla', 'CDN');
    await enviar();

    http.expectOne({ method: 'PATCH', url: URL }).flush(CLIENTE);
    await asentar();
    // La mutacion espera a la recarga que su invalidacion dispara: la sigla sale despues de ella.
    http.match(URL).forEach((p) => p.flush(CLIENTE));
    await asentar();
    http.expectOne({ method: 'PATCH', url: `${URL}/acronym` }).flush(
      { code: 'ERR_CLIENT_008', message: 'Client acronym already in use', details: [] },
      { status: 409, statusText: 'Conflict' },
    );
    await asentar();

    expect(texto()).toContain('Esa sigla ya la tiene otro cliente.');
    expect(raiz().querySelector<HTMLInputElement>('#sigla')!.value).toBe('CDN');
    http.match(URL).forEach((p) => p.flush(CLIENTE));
    await asentar();
  });

  it('sin pais, la clave no viaja', async () => {
    await abrir({ ...CLIENTE, idPais: undefined });
    escribir('razonSocial', 'Hospital del Norte');
    await enviar();

    const peticion = http.expectOne({ method: 'PATCH', url: URL });

    expect(peticion.request.body).not.toHaveProperty('idPais');
    peticion.flush(CLIENTE);
    await asentar();
    http.match(URL).forEach((p) => p.flush(CLIENTE));
    await asentar();
  });

  it('una razon social vacia no llega al servidor', async () => {
    await abrir();
    escribir('razonSocial', '');
    await enviar();

    http.expectNone({ method: 'PATCH', url: URL });
    expect(texto()).toContain('La razón social es obligatoria');
  });

  it('al guardar vuelve a la ficha del cliente', async () => {
    await abrir();
    escribir('razonSocial', 'Hospital del Norte');
    await enviar();
    http.expectOne({ method: 'PATCH', url: URL }).flush(CLIENTE);
    await asentar();
    http.match(URL).forEach((p) => p.flush(CLIENTE));
    await asentar();

    expect(TestBed.inject(Router).url).toBe(`/clientes/${ID}`);
  });

  it('un fallo del servidor se ensena traducido y no borra lo escrito', async () => {
    await abrir();
    escribir('razonSocial', 'Hospital del Norte');
    await enviar();
    http.expectOne({ method: 'PATCH', url: URL }).flush(
      { code: 'ERR_CLIENT_005', message: 'Invalid client data', details: ['Razón social inválida'] },
      { status: 400, statusText: 'Bad Request' },
    );
    await asentar();

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Revise los datos del formulario');
    expect(aviso?.textContent).toContain('Razón social inválida');
    expect(raiz().querySelector<HTMLInputElement>('#razonSocial')!.value).toBe('Hospital del Norte');
  });

  afterEach(() => http.verify());
});
