import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { elegirEnBuscador, escribirEnBuscador, sugerenciasDe } from '../../../../testing/pantalla';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import { NuevoClienteComponent } from './nuevo-cliente';

const URL = 'http://localhost:8081/v1/api/clients';
const URL_PAISES = 'http://localhost:8081/v1/api/countries';

/** Los paises que el catalogo devuelve en estas pruebas. */
const PAISES = [
  { id: 'co', nombre: 'Colombia', codigoIso: 'CO', estadoActivo: true },
  { id: 'pe', nombre: 'Perú', codigoIso: 'PE', estadoActivo: true },
];

/** Destinos de la navegacion posterior al alta. Vacios a proposito: aqui no se prueban esas pantallas. */
@Component({ selector: 'app-listado-falso', template: '' })
class ListadoFalso {}

@Component({ selector: 'app-ficha-falsa', template: '' })
class FichaFalsa {}

describe('Alta de un cliente', () => {
  let fixture: ComponentFixture<NuevoClienteComponent>;
  let http: HttpTestingController;
  let desinstalarAlmacenamiento: () => void;

  beforeEach(async () => {
    // El corredor de pruebas de Angular no expone localStorage, y el campo de busqueda recuerda ahi
    // lo ya elegido. En el navegador existe; lo que falta aqui es el doble.
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([
          { path: 'clientes', component: ListadoFalso },
          { path: 'clientes/:id', component: FichaFalsa },
        ]),
      ],
    });
    fixture = TestBed.createComponent(NuevoClienteComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // El catalogo de paises se pide al abrir la pantalla. Se responde aqui para que cada prueba
    // parta de un desplegable ya cargado; las que miran el fallo del catalogo lo hacen aparte.
    await responderPaises(PAISES);
  });

  /** Responde a la consulta del catalogo, que sale sola al construir la pantalla. */
  async function responderPaises(
    cuerpo: object,
    opciones?: { status: number; statusText: string },
  ): Promise<void> {
    for (let intento = 0; intento < 20; intento += 1) {
      await fixture.whenStable();
      fixture.detectChanges();
      const pendientes = http.match(URL_PAISES);
      if (pendientes.length) {
        if (opciones) {
          pendientes[0].flush(cuerpo, opciones);
        } else {
          pendientes[0].flush(cuerpo);
        }
        // Una vuelta no basta: la consulta resuelve por promesa y la senal tarda varios tics en
        // llegar al DOM. Esperar un numero fijo de tics seria una carrera.
        await asentar();

        return;
      }
      await new Promise((seguir) => setTimeout(seguir, 0));
    }

    throw new Error('La pantalla no pidio el catalogo de paises');
  }

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

  it('al guardar, lleva a la ficha del cliente creado', async () => {
    // Se navega de verdad contra una ruta real en vez de espiar al router: asi se comprueba que el
    // destino existe. Con un espia, una ruta mal escrita pasaria la prueba y fallaria en uso.
    //
    // Y el destino es la ficha, no el listado: lo siguiente que hace quien registra un cliente es
    // anadirle un correo o una sede, y las dos cosas estan ahi. Eso exige que el identificador de
    // la respuesta llegue a la ruta, que es lo que esta prueba fija.
    rellenarValido();
    await enviar();
    http.expectOne(URL).flush({ id: '1' });
    await asentar();

    expect(TestBed.inject(Router).url).toBe('/clientes/1');
  });

  describe('El país se busca escribiendo', () => {
    it('predice sobre el catálogo completo, sin tildes y sin mayúsculas', async () => {
      // Quien teclea «peru» en un telefono espera encontrar Perú. No hacerlo es la queja mas segura
      // de este tipo de campo.
      await escribirEnBuscador(fixture, 'idPais', 'peru');

      expect(sugerenciasDe(fixture, 'idPais')).toEqual(['Perú']);
    });

    it('sin escribir nada no ofrece los 249 países: solo lo ya usado en este navegador', async () => {
      // Abrir el campo y encontrarse el catalogo entero no ayuda a nadie. La primera vez no hay
      // historial, y entonces lo dice en vez de parecer roto.
      const campo = raiz().querySelector<HTMLInputElement>('#idPais')!;
      campo.dispatchEvent(new Event('focus'));
      fixture.detectChanges();

      expect(sugerenciasDe(fixture, 'idPais')).toEqual([]);
      expect(texto()).toContain('Escriba para buscar');
    });

    it('lo elegido antes se ofrece la próxima vez, sin escribir nada', async () => {
      await elegirEnBuscador(fixture, 'idPais', 'Colombia');

      // Se monta la pantalla otra vez: el historial vive en el navegador, no en el componente.
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          ...proveerApiSimulado(),
          provideRouter([
            { path: 'clientes', component: ListadoFalso },
            { path: 'clientes/:id', component: FichaFalsa },
          ]),
        ],
      });
      fixture = TestBed.createComponent(NuevoClienteComponent);
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();
      await responderPaises(PAISES);

      const campo = raiz().querySelector<HTMLInputElement>('#idPais')!;
      campo.dispatchEvent(new Event('focus'));
      fixture.detectChanges();

      expect(sugerenciasDe(fixture, 'idPais')).toEqual(['Colombia']);
    });

    it('un nombre que no existe se dice y no se guarda', async () => {
      // El backend exige un identificador que exista: inventarlo daria un error tecnico al guardar.
      rellenarValido();
      await escribirEnBuscador(fixture, 'idPais', 'Wakanda');
      await enviar();

      const peticion = http.expectOne(URL);

      expect(peticion.request.body).not.toHaveProperty('idPais');
      expect(texto()).toContain('No hay ninguna opción con ese nombre');
      peticion.flush({ id: '1' });
    });

    it('cuando se elige un país, va en el cuerpo como identificador', async () => {
      rellenarValido();
      await elegirEnBuscador(fixture, 'idPais', 'Colombia');
      await enviar();

      const peticion = http.expectOne(URL);

      expect(peticion.request.body).toEqual({
        razonSocial: 'Hospital Central',
        tipoIdentificacion: 'NIT_JURIDICO',
        documento: '900123456',
        idPais: 'co',
      });
      peticion.flush({ id: '1' });
    });

    it('sin país elegido, la clave no viaja: una cadena vacía no es un identificador', async () => {
      // Mandar idPais: "" haria que el backend lo tratara como una referencia inexistente y
      // devolviera "ese pais no existe" en lugar de entender que no se sabe.
      rellenarValido();
      await enviar();

      const peticion = http.expectOne(URL);

      expect(peticion.request.body).not.toHaveProperty('idPais');
      peticion.flush({ id: '1' });
    });
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
      for (const id of ['razonSocial', 'tipoIdentificacion', 'documento', 'idPais']) {
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

    it('los obligatorios llevan asterisco y el opcional no, con su leyenda', () => {
      // La convencion se invirtio el 2026-09-26: se marca lo obligatorio en vez de repetir
      // «opcional» en cada campo que no lo es.
      for (const id of ['razonSocial', 'tipoIdentificacion', 'documento']) {
        expect(raiz().querySelector(`label[for="${id}"]`)!.textContent).toContain('*');
        expect(raiz().querySelector(`#${id}`)!.getAttribute('aria-required')).toBe('true');
      }

      expect(raiz().querySelector('label[for="idPais"]')!.textContent).not.toContain('*');
      // Un asterisco sin leyenda es un simbolo sin significado declarado.
      expect(texto()).toContain('son obligatorios');
    });

    it('los controles respetan el area tactil minima', () => {
      for (const control of raiz().querySelectorAll('input, select, button, form a')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });
  });

  afterEach(() => {
    http.verify();
    desinstalarAlmacenamiento();
  });
});
