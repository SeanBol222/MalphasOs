import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../testing/entorno';
import { ClienteApi } from './cliente-api';

const URL = 'http://localhost:8081/v1/api/clients';

describe('Puerta al API de clientes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: proveerApiSimulado() });
    http = TestBed.inject(HttpTestingController);
  });

  async function asentar(): Promise<void> {
    for (let i = 0; i < 20; i += 1) {
      await new Promise((seguir) => setTimeout(seguir, 0));
    }
  }

  it('dar de alta un cliente vuelve a pedir la lista', async () => {
    // Es lo unico que impide el defecto clasico: creas un cliente, vuelves al listado y no esta
    // porque la cache sigue teniendo la lista de antes. Sin esta prueba, quitar la invalidacion
    // no rompia nada.
    const { lista, alta } = TestBed.runInInjectionContext(() => {
      const api = TestBed.inject(ClienteApi);

      return { lista: api.listar(), alta: api.crear() };
    });

    await asentar();
    http.expectOne({ method: 'GET', url: URL }).flush([]);
    await asentar();
    expect(lista.data()).toEqual([]);

    alta.mutate({ razonSocial: 'Hospital', tipoIdentificacion: 'CC', documento: '1' });
    await asentar();
    http.expectOne({ method: 'POST', url: URL }).flush({ id: '1' });
    await asentar();

    // La segunda peticion es la que demuestra que la lista se invalido.
    http.expectOne({ method: 'GET', url: URL }).flush([{ id: '1', razonSocial: 'Hospital' }]);
    await asentar();

    expect(lista.data()).toHaveLength(1);
  });

  afterEach(() => http.verify());
});
