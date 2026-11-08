import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { vi } from 'vitest';
import { proveerApiSimulado } from '../../../testing/entorno';
import { InformeDeCarga } from '../../core/api/tipos';
import { CargaApi } from './carga-api';

const URL = 'http://localhost:8081/v1/api/data-loads';

const LIMPIO: InformeDeCarga = {
  valida: true,
  errores: [],
  avisos: [],
  resumen: [{ seccion: 'marcas', nuevos: 2, existentes: 0 }],
};
const CON_ERRORES: InformeDeCarga = {
  valida: false,
  errores: [{ archivo: '3-historial.json', ruta: '/ordenes/1', mensaje: 'MalphasOS no lo aceptó' }],
  avisos: [],
  resumen: [],
};

const json = (nombre: string) => new File(['{}'], nombre, { type: 'application/json' });

describe('Puerta al API de la carga de datos (2026-11-08)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: proveerApiSimulado() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function asentar(): Promise<void> {
    for (let i = 0; i < 20; i += 1) {
      await new Promise((seguir) => setTimeout(seguir, 0));
    }
  }

  function api() {
    return TestBed.runInInjectionContext(() => {
      const carga = TestBed.inject(CargaApi);
      return { validar: carga.validar(), aplicar: carga.aplicar() };
    });
  }

  it('validar sube cada archivo en el campo archivos, con su nombre, y devuelve el informe', async () => {
    const { validar } = api();

    validar.mutate([json('1-catalogo.json'), json('2-cliente.json')]);
    await asentar();
    const peticion = http.expectOne({ method: 'POST', url: `${URL}/validation` });
    const cuerpo = peticion.request.body as FormData;
    expect(cuerpo.getAll('archivos').map((a) => (a as File).name)).toEqual([
      '1-catalogo.json',
      '2-cliente.json',
    ]);
    peticion.flush(LIMPIO);
    await asentar();

    expect(validar.data()).toEqual(LIMPIO);
  });

  it('un 422 al aplicar es un informe, no un fallo: la carga no se escribio y dice por que', async () => {
    const { aplicar } = api();

    aplicar.mutate([json('3-historial.json')]);
    await asentar();
    http
      .expectOne({ method: 'POST', url: URL })
      .flush(CON_ERRORES, { status: 422, statusText: 'Unprocessable Content' });
    await asentar();

    expect(aplicar.isError()).toBe(false);
    expect(aplicar.data()).toEqual(CON_ERRORES);
  });

  it('otro error al aplicar si es un fallo', async () => {
    const { aplicar } = api();

    aplicar.mutate([json('1-catalogo.json')]);
    await asentar();
    http
      .expectOne({ method: 'POST', url: URL })
      .flush({ code: 'ERR' }, { status: 403, statusText: 'Forbidden' });
    await asentar();

    expect(aplicar.isError()).toBe(true);
  });

  it('una carga aplicada invalida toda la cache: crea de todo a la vez', async () => {
    const invalidar = vi.spyOn(TestBed.inject(QueryClient), 'invalidateQueries');
    const { aplicar } = api();

    aplicar.mutate([json('1-catalogo.json')]);
    await asentar();
    http.expectOne({ method: 'POST', url: URL }).flush(LIMPIO);
    await asentar();

    expect(invalidar).toHaveBeenCalledWith();
  });

  it('una rechazada no invalida nada: no se escribio nada', async () => {
    const invalidar = vi.spyOn(TestBed.inject(QueryClient), 'invalidateQueries');
    const { aplicar } = api();

    aplicar.mutate([json('1-catalogo.json')]);
    await asentar();
    http
      .expectOne({ method: 'POST', url: URL })
      .flush(CON_ERRORES, { status: 422, statusText: 'Unprocessable Content' });
    await asentar();

    expect(invalidar).not.toHaveBeenCalled();
  });
});
