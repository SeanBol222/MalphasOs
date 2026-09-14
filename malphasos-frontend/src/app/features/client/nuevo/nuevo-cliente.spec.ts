import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { NuevoClienteComponent } from './nuevo-cliente';

const URL = 'http://localhost:8081/v1/api/clients';

/** Destino de la vuelta al listado. Vacio a proposito: no se prueba el listado aqui. */
@Component({ selector: 'app-listado-falso', template: '' })
class ListadoFalso {}

describe('Alta de un cliente', () => {
  let fixture: ComponentFixture<NuevoClienteComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'clientes', component: ListadoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoClienteComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function rellenarValido(): void {
    escribir('razonSocial', 'Hospital Central');
    escribir('documento', '900123456');
  }

  /**
   * Envia el formulario y espera a que la peticion salga.
   *
   * <p>La mutacion es asincrona: sin esperar, {@code expectOne} corre antes de que el POST exista y
   * la peticion queda abierta, lo que ademas rompe la prueba siguiente al fallar {@code verify}.
   */
  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((seguir) => setTimeout(seguir, 0));
    fixture.detectChanges();
  }

  async function asentar(): Promise<void> {
    for (let i = 0; i < 20; i += 1) {
      await fixture.whenStable();
      fixture.detectChanges();
      await new Promise((seguir) => setTimeout(seguir, 0));
    }
  }

  it('un formulario vacio no llega al servidor, y dice por que', async () => {
    // Sin marcar todo como tocado, pulsar Guardar en un formulario vacio no diria nada.
    await enviar();

    http.expectNone(URL);
    expect(texto()).toContain('La razón social es obligatoria');
    expect(texto()).toContain('El documento es obligatorio');
  });

  it('los errores no aparecen antes de tocar el campo', () => {
    // Ensenar "es obligatorio" en un formulario recien abierto trata al usuario como si ya se
    // hubiera equivocado.
    expect(texto()).not.toContain('es obligatoria');
  });

  it('manda exactamente lo que el contrato declara, ni un campo mas', async () => {
    rellenarValido();
    await enviar();

    const peticion = http.expectOne(URL);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({
      razonSocial: 'Hospital Central',
      tipoIdentificacion: 'NIT_JURIDICO',
      documento: '900123456',
    });
    peticion.flush({ id: '1' });
  });

  it('las cotas de los campos salen del contrato, no de una suposicion', () => {
    // documento <= 11 y razon social <= 50 los declara el backend. Si los cambia, se regeneran los
    // tipos y esto queda desalineado a la vista.
    expect(raiz().querySelector<HTMLInputElement>('#documento')!.maxLength).toBe(11);
    expect(raiz().querySelector<HTMLInputElement>('#razonSocial')!.maxLength).toBe(50);
  });

  it('al guardar, vuelve al listado', async () => {
    // Se navega de verdad contra una ruta real en vez de espiar al router: asi se comprueba que el
    // destino existe. Con un espia, una ruta mal escrita pasaria la prueba y fallaria en uso.
    rellenarValido();
    await enviar();
    http.expectOne(URL).flush({ id: '1' });
    await asentar();

    expect(TestBed.inject(Router).url).toBe('/clientes');
  });

  it('un fallo del servidor se ensena traducido y con su detalle, y no se pierde lo escrito', async () => {
    rellenarValido();
    await enviar();
    http.expectOne(URL).flush(
      { code: 'ERR_CLIENT_005', message: 'Invalid client data', details: ['El documento ya existe'] },
      { status: 400, statusText: 'Bad Request' },
    );
    await asentar();

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Revise los datos del formulario');
    expect(aviso?.textContent).toContain('El documento ya existe');
    // Perder lo escrito obligaria a teclearlo todo otra vez por un error del servidor.
    expect(raiz().querySelector<HTMLInputElement>('#razonSocial')!.value).toBe('Hospital Central');
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('cada campo tiene su etiqueta asociada', () => {
      for (const id of ['razonSocial', 'tipoIdentificacion', 'documento']) {
        expect(raiz().querySelector(`label[for="${id}"]`)).toBeTruthy();
      }
    });

    it('un campo invalido se marca y apunta a su mensaje', async () => {
      // El color rojo no le dice nada a quien no ve la pantalla: hace falta aria-invalid y que el
      // mensaje este vinculado al campo.
      await enviar();
      const campo = raiz().querySelector<HTMLInputElement>('#documento')!;

      expect(campo.getAttribute('aria-invalid')).toBe('true');
      expect(raiz().querySelector(`#${campo.getAttribute('aria-describedby')}`)).toBeTruthy();
    });

    it('los controles respetan el area tactil minima', () => {
      for (const control of raiz().querySelectorAll('input, select, button, form a')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });
  });

  afterEach(() => http.verify());
});
