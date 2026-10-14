import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  CLIENTES,
  EQUIPOS,
  ID_ORDEN,
  ID_SEDE,
  orden,
  PERSONAS,
  SEDE,
  URL_CLIENTES,
  URL_EQUIPOS_DE_CLIENTE,
  URL_ORDENES,
  URL_PERSONAS,
  urlSede,
} from '../../../../testing/ordenes';
import {
  // El catalogo llama EQUIPOS a los del catalogo y ordenes.ts a las unidades del cliente: son dos cosas
  // distintas con el mismo nombre, que es la ambiguedad del dominio anotada en el wiki. Se renombran
  // aqui para que la prueba no la herede.
  EQUIPOS as EQUIPOS_DE_CATALOGO,
  MODELOS,
  TIPOS,
  URL_EQUIPOS as URL_EQUIPOS_DE_CATALOGO,
  URL_MODELOS,
  URL_TIPOS,
} from '../../../../testing/catalogo';
import {
  ID_REPORTE,
  reporte,
  reporteCerrable,
  urlCerrarReporte,
  urlReporte,
} from '../../../../testing/reportes';
import { ReporteApi } from '../reporte-api';
import { DetalleReporte } from './detalle-reporte';

const TODAS = [
  'report.read',
  'report.write',
  'equipment.read',
  // Lo que RF-11 autocompleta se consulta de verdad, asi que la pantalla necesita leer esos modulos.
  'work-order.read',
  'client.read',
  'person.read',
  'engineer.read',
];

const URL_ENCARGADOS = 'http://localhost:8081/v1/api/managers';

/** Un encargado de la sede de la orden, que es el responsable del cliente en ese sitio. */
const ENCARGADOS = [
  { idPersona: 'p2', idSede: ID_SEDE, idAreaServicio: undefined, tipo: 'SEDE', estadoActivo: true },
];

@Component({ selector: 'app-orden-falsa', template: '' })
class OrdenFalsa {}

describe('Ficha de un reporte de servicio', () => {
  let fixture: ComponentFixture<DetalleReporte>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = TODAS): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([
          { path: 'ordenes/:id', component: OrdenFalsa },
          { path: 'ordenes', component: OrdenFalsa },
        ]),
      ],
    });
    fixture = TestBed.createComponent(DetalleReporte);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_REPORTE);
  }

  /**
   * Se monta antes de cada prueba, y el caso «sin autoridad» rehace el TestBed.
   *
   * <p>Montar dentro de la prueba en vez de aquí no funciona, y conviene saber por qué: el entorno de
   * pruebas de Angular reinicia el TestBed en un {@code beforeEach} propio, de modo que la primera
   * prueba monta bien y la segunda encuentra el módulo ya instanciado. Para cambiar de autoridades hay
   * que reiniciarlo a mano, que es lo que hace la prueba de abajo.
   */
  beforeEach(() => montar());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const campo = (id: string) => raiz().querySelector<HTMLTextAreaElement>(`#${id}`)!;
  /** Las tres opciones del resultado: desde el 2026-10-10 son radios y no un desplegable. */
  const opcionesDeResultado = () =>
    [...raiz().querySelectorAll<HTMLInputElement>('input[type="radio"][name="resultado"]')];
  const resultadoElegido = () => opcionesDeResultado().find((opcion) => opcion.checked)?.value;

  /**
   * Abre la ficha y responde a sus cinco consultas.
   *
   * <p>Son cinco porque la tabla de verificación tiene que averiguar cómo se verifica el equipo, y eso
   * son cuatro eslabones del catálogo: unidad → modelo → equipo del catálogo → tipo. El backend camina
   * la misma cadena por la misma razón — ninguna tabla intermedia guarda un atajo hacia el tipo.
   */
  async function abrir(datos: object = reporte(), laOrden: object = orden()): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, urlReporte(ID_REPORTE), datos);
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((peticion) => peticion.flush(EQUIPOS));
    http.match(URL_MODELOS).forEach((peticion) => peticion.flush(MODELOS));
    http.match(URL_EQUIPOS_DE_CATALOGO).forEach((peticion) => peticion.flush(EQUIPOS_DE_CATALOGO));
    http.match(URL_TIPOS).forEach((peticion) => peticion.flush(TIPOS));
    // Y lo que RF-11 autocompleta: la orden, su cliente, su sede, las personas y los encargados.
    http.match(`${URL_ORDENES}/${ID_ORDEN}`).forEach((peticion) => peticion.flush(laOrden));
    http.match(URL_CLIENTES).forEach((peticion) => peticion.flush(CLIENTES));
    http.match(URL_PERSONAS).forEach((peticion) => peticion.flush(PERSONAS));
    http.match(URL_ENCARGADOS).forEach((peticion) => peticion.flush(ENCARGADOS));
    await asentar(fixture);
    http.match(urlSede(ID_SEDE)).forEach((peticion) => peticion.flush(SEDE));
    await asentar(fixture);
  }

  /**
   * Responde a lo que quede abierto tras una escritura, hasta que no quede nada.
   *
   * <p>Se drena en vueltas y no de una pasada: la recarga que provoca invalidar la caché no sale en el
   * mismo tic que la escritura, de modo que un único {@code match} encuentra la lista vacía y el
   * {@code verify()} del final falla por una petición que llegó después. Cada cuerpo se elige por la
   * URL, porque responder cualquier cosa a cualquier consulta rompe la pantalla por otro lado.
   */
  async function atenderTodo(datos: object = reporte()): Promise<void> {
    const cuerpos: [string, object][] = [
      [urlReporte(ID_REPORTE), datos],
      [URL_EQUIPOS_DE_CLIENTE, EQUIPOS],
      [URL_MODELOS, MODELOS],
      [URL_EQUIPOS_DE_CATALOGO, EQUIPOS_DE_CATALOGO],
      [URL_TIPOS, TIPOS],
      [`${URL_ORDENES}/${ID_ORDEN}`, orden()],
      [URL_CLIENTES, CLIENTES],
      [URL_PERSONAS, PERSONAS],
      [URL_ENCARGADOS, ENCARGADOS],
      [urlSede(ID_SEDE), SEDE],
    ];

    // Se dan todas las vueltas, sin salir en la primera que no encuentre nada: la recarga tarda en
    // aparecer, asi que una vuelta vacia no significa que no vaya a llegar. Salir antes dejaba una
    // peticion abierta, el verify() de la prueba lanzaba desde afterEach, y eso impedia que Angular
    // desmontara el TestBed: la prueba siguiente fallaba con «el modulo ya esta instanciado» y las diez
    // siguientes con ella. Un solo fallo real se leia como once.
    for (let vuelta = 0; vuelta < 5; vuelta += 1) {
      for (const [url, cuerpo] of cuerpos) {
        http.match(url).forEach((peticion) => peticion.flush(cuerpo));
      }

      await asentar(fixture);
    }
  }

  function pulsar(etiqueta: string): void {
    // Texto exacto y no includes: la tabla de verificacion tiene su propio boton de guardar, y desde
    // que va encima del del reporte un includes pulsaba ese. Es la trampa de CONVENCIONES.md.
    const boton = [...raiz().querySelectorAll('button')].find(
      (b) => (b.textContent ?? '').trim() === etiqueta,
    );

    if (!boton) {
      throw new Error(`No hay ningun boton que diga "${etiqueta}"`);
    }

    boton.click();
    fixture.detectChanges();
  }

  function escribir(id: string, valor: string): void {
    const control = campo(id);
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  it('enseña de qué equipo habla y en qué estado está', async () => {
    await abrir();

    // La serie sale de la lista de equipos: el reporte solo trae el identificador.
    expect(texto()).toContain('SN-0001');
    expect(texto()).toContain('Borrador');
  });

  it('autocompleta cliente, sede, servicio y responsables desde la orden (RF-11)', async () => {
    await abrir();

    expect(texto()).toContain('Hospital Central');
    expect(texto()).toContain('Sede Norte');
    expect(texto()).toContain('Preventivo · Trimestral');
    expect(texto()).toContain('2026-10-15');
    // El encargado de la sede sale de la lista completa de encargados: el API no publica los de una sede.
    expect(texto()).toContain('Luis Peña');
  });

  it('lo autocompletado no tiene ningún campo que editar (RNF-07)', async () => {
    await abrir();

    // Cuatro areas de texto y tres radios: son los cinco campos de RF-15 —el quinto, el resultado, son
    // los radios desde el 2026-10-10; antes era un desplegable— y ni uno mas. Si algun dato de la orden llegara como campo editable, aqui
    // saldria un control de sobra. Los numericos de la verificacion no cuentan: son lecturas, no datos
    // autocompletados.
    // Desde el 2026-10-14 se dicen por su nombre y no se cuentan: el correctivo trajo campos nuevos, y
    // con un número cualquier campo de sobra que cuadrara la cuenta pasaría.
    expect([...raiz().querySelectorAll('textarea')].map((t) => t.id)).toEqual([
      'fallaReportada',
      'diagnostico',
      'procedimientos',
      'repuestos',
      'observaciones',
    ]);
    expect(raiz().querySelectorAll('select')).toHaveLength(0);
    expect(opcionesDeResultado()).toHaveLength(3);
    expect(
      [...raiz().querySelectorAll('input')].filter((i) => i.type === 'text').length,
    ).toBe(0);
  });

  it('una orden sin ingeniero lo dice, en vez de dejar el hueco en blanco', async () => {
    await abrir();

    expect(texto()).toContain('Sin asignar en la orden');
  });

  it('vuelca en el formulario los cinco campos que el servidor tiene', async () => {
    await abrir(
      reporte({
        fallaReportada: 'No enciende',
        diagnostico: 'Fuente quemada',
        procedimientos: 'Cambio de fuente',
        observaciones: 'Queda en prueba',
        resultado: 'OPERATIVO_CON_RESTRICCIONES',
      }),
    );

    expect(campo('fallaReportada').value).toBe('No enciende');
    expect(campo('diagnostico').value).toBe('Fuente quemada');
    expect(campo('procedimientos').value).toBe('Cambio de fuente');
    expect(campo('observaciones').value).toBe('Queda en prueba');
    expect(resultadoElegido()).toBe('OPERATIVO_CON_RESTRICCIONES');
  });

  it('guardar manda los cinco campos tal como se ven', async () => {
    await abrir();
    escribir('fallaReportada', 'No enciende');
    escribir('procedimientos', 'Cambio de fuente');

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body.fallaReportada).toBe('No enciende');
    expect(peticion.request.body.procedimientos).toBe('Cambio de fuente');
    peticion.flush(reporte({ fallaReportada: 'No enciende', procedimientos: 'Cambio de fuente' }));
    await atenderTodo();
  });

  describe('El correctivo (2026-10-14)', () => {
    const CORRECTIVO = orden({ estadoEjecucion: 'EN_EJECUCION', tipoServicio: 'CORRECTIVO' });
    const botonCerrar = () =>
      [...raiz().querySelectorAll('button')].find(
        (b) => b.textContent?.trim() === 'Cerrar reporte',
      ) as HTMLButtonElement;

    it('pide la falla y el diagnóstico como obligatorios, y ofrece la causa', async () => {
      await abrir(reporte(), CORRECTIVO);

      expect(campo('fallaReportada').getAttribute('aria-required')).toBe('true');
      expect(campo('diagnostico').getAttribute('aria-required')).toBe('true');
      expect(raiz().querySelector('#causa')).not.toBeNull();
      expect(raiz().querySelector('label[for="procedimientos"]')?.textContent).toContain(
        'Trabajo realizado',
      );
    });

    it('un preventivo no los exige ni ofrece la causa', async () => {
      await abrir();

      expect(campo('fallaReportada').getAttribute('aria-required')).toBeNull();
      expect(raiz().querySelector('#causa')).toBeNull();
    });

    it('sin falla ni diagnóstico no se puede cerrar, aunque tenga trabajo y resultado', async () => {
      await abrir(reporteCerrable(), CORRECTIVO);

      expect(botonCerrar().disabled).toBe(true);
      expect(texto()).toContain('Falla reportada');
      expect(texto()).toContain('hacen falta la falla reportada, el diagnóstico');
    });

    it('con falla y diagnóstico guardados se puede cerrar', async () => {
      await abrir(
        reporteCerrable({ fallaReportada: 'No enciende', diagnostico: 'Fusible abierto' }),
        CORRECTIVO,
      );

      expect(botonCerrar().disabled).toBe(false);
    });

    it('guardar manda la causa y los repuestos', async () => {
      await abrir(reporte(), CORRECTIVO);
      escribir('causa', 'Sobretensión');
      escribir('repuestos', 'Fusible 2 A');

      pulsar('Guardar');
      await asentar(fixture);

      const peticion = http.expectOne(urlReporte(ID_REPORTE));
      expect(peticion.request.body.causa).toBe('Sobretensión');
      expect(peticion.request.body.repuestos).toBe('Fusible 2 A');
      peticion.flush(reporte({ causa: 'Sobretensión', repuestos: 'Fusible 2 A' }));
      await atenderTodo();
    });
  });

  it('un campo que se vacía se manda vacío, porque vaciarlo es borrarlo', async () => {
    // El backend distingue el nulo -«no lo cambies»- del blanco -«bórralo»-, y este formulario muestra
    // todo lo que hay: vaciar una casilla en pantalla significa borrar el dato.
    await abrir(reporte({ fallaReportada: 'No enciende' }));
    escribir('fallaReportada', '');

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.body.fallaReportada).toBe('');
    peticion.flush(reporte());
    await atenderTodo();
  });

  it('sin resultado elegido no se manda la cadena vacía, que no es un valor del catálogo', async () => {
    await abrir();
    escribir('procedimientos', 'Limpieza');

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.body.resultado).toBeUndefined();
    peticion.flush(reporte({ procedimientos: 'Limpieza' }));
    await atenderTodo();
  });

  it('elegir el resultado con su opción lo manda al guardar', async () => {
    await abrir();
    const fuera = opcionesDeResultado().find((opcion) => opcion.value === 'FUERA_DE_SERVICIO')!;
    fuera.click();
    fixture.detectChanges();

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.body.resultado).toBe('FUERA_DE_SERVICIO');
    peticion.flush(reporte({ resultado: 'FUERA_DE_SERVICIO' }));
    await atenderTodo();
  });

  describe('La lista de verificación del protocolo (2026-10-11)', () => {
    const PASOS = [
      { orden: 1, idProtocolo: 'pr1', nombre: 'Limpieza', descripcion: 'Paño.', indicacion: 'Con guantes', estado: 'PENDIENTE' as const },
      { orden: 2, idProtocolo: 'pr2', nombre: 'Batería', descripcion: 'Revisar la carga.', estado: 'PENDIENTE' as const },
    ];
    const conPasos = (cambios: object = {}) => reporte({ pasos: PASOS, ...cambios });
    const casilla = (orden: number) =>
      raiz().querySelector<HTMLButtonElement>(`li[data-paso="${orden}"] button[role="checkbox"]`)!;
    const panel = () => raiz().querySelector('aside')!.textContent ?? '';

    /** Responde el PATCH de un paso con el reporte entero, como hace el servidor. */
    async function responderPaso(orden: number, devuelto: object): Promise<{ estado: string; nota?: string }> {
      const peticion = http.expectOne({ method: 'PATCH', url: `${urlReporte(ID_REPORTE)}/steps/${orden}` });
      const cuerpo = peticion.request.body;
      peticion.flush(devuelto);
      await asentar(fixture);

      return cuerpo;
    }

    it('pinta los pasos en orden con su indicación, y el texto pasa a trabajo adicional sin asterisco', async () => {
      await abrir(conPasos());

      expect(
        [...raiz().querySelectorAll('li[data-paso]')].map((li) => li.getAttribute('data-paso')),
      ).toEqual(['1', '2']);
      expect(texto()).toContain('Para este tipo: Con guantes');
      expect(raiz().querySelector('label[for="procedimientos"]')!.textContent).toContain('Trabajo adicional');
      expect(campo('procedimientos').getAttribute('aria-required')).toBeNull();
    });

    it('pulsar la casilla marca el paso al momento, sin pasar por guardar ni recargar', async () => {
      await abrir(conPasos());
      casilla(1).click();
      await asentar(fixture);

      expect(await responderPaso(1, conPasos({ pasos: [{ ...PASOS[0], estado: 'HECHO' }, PASOS[1]] }))).toEqual({
        estado: 'HECHO',
      });
      // La respuesta se pone en la cache: no hay una recarga del reporte detras.
      expect(http.match(urlReporte(ID_REPORTE))).toHaveLength(0);
      expect(casilla(1).getAttribute('aria-checked')).toBe('true');
    });

    it('volver a pulsarla lo deja pendiente otra vez', async () => {
      await abrir(conPasos({ pasos: [{ ...PASOS[0], estado: 'HECHO' }, PASOS[1]] }));
      casilla(1).click();
      await asentar(fixture);

      expect((await responderPaso(1, conPasos())).estado).toBe('PENDIENTE');
    });

    it('«no aplica» pide la nota y no deja marcarlo sin ella', async () => {
      await abrir(conPasos());
      pulsar('No aplica');
      await asentar(fixture);

      const marcar = [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Marcar no aplica')!;
      expect(marcar.disabled).toBe(true);

      const nota = raiz().querySelector<HTMLInputElement>('#nota-1')!;
      nota.value = ' El equipo no tiene batería ';
      nota.dispatchEvent(new Event('input'));
      await asentar(fixture);
      marcar.click();
      await asentar(fixture);

      expect(
        await responderPaso(
          1,
          conPasos({ pasos: [{ ...PASOS[0], estado: 'NO_APLICA', nota: 'El equipo no tiene batería' }, PASOS[1]] }),
        ),
      ).toEqual({ estado: 'NO_APLICA', nota: 'El equipo no tiene batería' });
      expect(texto()).toContain('no aplica');
      expect(texto()).toContain('Nota: El equipo no tiene batería');
    });

    it('el panel cuenta los pasos, y con todos marcados y resultado se cierra sin trabajo adicional', async () => {
      await abrir(conPasos({ resultado: 'OPERATIVO' }));

      expect(panel()).toContain('Protocolo: 0 de 2 pasos');
      // El avance cuenta lo marcado del protocolo, no solo si esta completo: aqui ningun paso y el
      // resultado son la mitad.
      expect(panel()).toContain('50 %');
      const cerrar = () =>
        [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Cerrar reporte')!;
      expect(cerrar().disabled).toBe(true);
    });

    it('el avance cuenta los pasos marcados, no solo el protocolo completo', async () => {
      // Con dos de tres pasos el panel decia 0 %: un requisito incompleto contaba como nada. Este caso
      // es el que separa las dos formulas, porque con cero pasos marcados las dos dan lo mismo.
      await abrir(conPasos({ resultado: 'OPERATIVO', pasos: [{ ...PASOS[0], estado: 'HECHO' }, PASOS[1]] }));

      expect(panel()).toContain('75 %');
    });

    it('con todos los pasos marcados, sin texto escrito, deja cerrar', async () => {
      // Es la regla de V22: con pasos el texto es opcional. Antes de ella este reporte no se cerraba.
      await abrir(
        conPasos({
          resultado: 'OPERATIVO',
          pasos: [
            { ...PASOS[0], estado: 'HECHO' },
            { ...PASOS[1], estado: 'NO_APLICA', nota: 'No tiene' },
          ],
        }),
      );

      expect(panel()).toContain('Protocolo: 2 de 2 pasos');
      expect(panel()).toContain('100 %');
      const cerrar = [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Cerrar reporte')!;
      expect(cerrar.disabled).toBe(false);
    });

    it('un reporte cerrado no deja marcar ni ofrece «no aplica»', async () => {
      await abrir(
        conPasos({
          estado: 'FINALIZADO',
          resultado: 'OPERATIVO',
          finalizado: '2026-10-11T10:00:00',
          pasos: [{ ...PASOS[0], estado: 'HECHO' }, { ...PASOS[1], estado: 'HECHO' }],
        }),
      );

      expect(casilla(1).disabled).toBe(true);
      expect([...raiz().querySelectorAll('button')].some((b) => b.textContent?.trim() === 'No aplica')).toBe(false);
    });
  });

  describe('El panel de cierre', () => {
    const panel = () => raiz().querySelector('aside')!.textContent ?? '';

    it('cuenta lo que falta de lo que el servidor exige, y lo dice en palabras', async () => {
      await abrir(reporte({ procedimientos: 'Limpieza' }));

      expect(panel()).toContain('50 %');
      expect(panel()).toContain('Procedimientos');
      expect(panel()).toContain('(listo)');
      expect(panel()).toContain('(falta)');
    });

    it('con lo exigido guardado llega al 100 % y deja cerrar', async () => {
      await abrir(reporteCerrable());

      expect(panel()).toContain('100 %');
      expect(panel()).not.toContain('(falta)');
    });

    it('cuenta lo guardado y no lo escrito: cerrar opera sobre lo que el servidor tiene', async () => {
      // Escribir los procedimientos sin guardar no acerca el cierre: el servidor no los tiene.
      await abrir();
      escribir('procedimientos', 'Limpieza');
      // Una recarga del reporte hace que el panel se recalcule: sin ella la prueba pasaria igual
      // leyendo lo escrito, porque el formulario no es una senal. Lo destapo una mutacion.
      // La recarga trae otro diagnostico y sigue sin procedimientos: con datos iguales TanStack conserva
      // la misma referencia y el panel ni se recalcularia.
      void TestBed.inject(QueryClient).invalidateQueries({ queryKey: ReporteApi.CLAVE });
      await atenderTodo(reporte({ diagnostico: 'Revisado por otra persona' }));

      // Ni un requisito listo. Se comprueba asi y no buscando «0 %», porque «50 %» tambien lo contiene:
      // la primera version de esta prueba pasaba con el defecto dentro.
      expect(panel()).not.toContain('(listo)');
      expect(texto()).toContain('Hay cambios sin guardar');
    });

    it('un reporte cerrado no enseña la cuenta, dice que está cerrado', async () => {
      await abrir(reporteCerrable({ estado: 'FINALIZADO', finalizado: '2026-09-27T10:00:00' }));

      expect(panel()).toContain('Reporte cerrado');
      expect(panel()).not.toContain('%');
    });
  });

  it('no deja cerrar sin procedimientos ni resultado, que es lo que el servidor exige', async () => {
    await abrir();

    const cerrar = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Cerrar reporte'),
    )!;
    expect(cerrar.disabled).toBe(true);
    expect(texto()).toContain('Para cerrarlo hacen falta los procedimientos y el resultado');
  });

  it('con los dos campos, cerrar pide confirmación y avisa de que no se podrá modificar', async () => {
    await abrir(reporteCerrable());

    pulsar('Cerrar reporte');

    expect(texto()).toContain('Un reporte cerrado ya no se modifica');

    pulsar('Confirmar cierre');
    await asentar(fixture);

    const peticion = http.expectOne(urlCerrarReporte(ID_REPORTE));
    expect(peticion.request.method).toBe('PATCH');
    peticion.flush(reporteCerrable({ estado: 'FINALIZADO', finalizado: '2026-09-27T10:00:00' }));
    await asentar(fixture);
    await atenderTodo(reporteCerrable());
  });

  it('cerrar un reporte deja caducados la hoja de vida y el listado de equipos, porque el servidor les anota el servicio', async () => {
    // El servidor escribe la intervención solo al cerrar; la pantalla tiene que enterarse. Sin esto, la
    // hoja de vida que ya estuviera en caché seguiría diciendo «sin intervenciones» hasta recargar.
    const cache = TestBed.inject(QueryClient);
    // El cliente de pruebas tiene gcTime 0, que borra al instante una consulta sin observadores: sin
    // esto, la hoja de vida sembrada desaparecía antes del cierre y la prueba fallaba por la caché de
    // pruebas, no por el código. La primera versión cayó justo ahí.
    cache.setQueryDefaults(['hoja-de-vida'], { gcTime: Infinity });
    cache.setQueryData(['hoja-de-vida', 'cualquier-equipo'], { servicioTecnico: [] });
    // Y el listado de equipos, que desde el 2026-10-11 enseña el último servicio de cada uno.
    const RESUMEN = ['equipos-de-cliente', 'resumen', { pagina: 0, tamano: 25 }];
    cache.setQueryDefaults(['equipos-de-cliente'], { gcTime: Infinity });
    cache.setQueryData(RESUMEN, { filas: [], total: 0 });
    await abrir(reporteCerrable());

    pulsar('Cerrar reporte');
    pulsar('Confirmar cierre');
    await asentar(fixture);

    http
      .expectOne(urlCerrarReporte(ID_REPORTE))
      .flush(reporteCerrable({ estado: 'FINALIZADO', finalizado: '2026-09-27T10:00:00' }));
    await asentar(fixture);
    await atenderTodo(reporteCerrable());

    expect(cache.getQueryState(['hoja-de-vida', 'cualquier-equipo'])?.isInvalidated).toBe(true);
    expect(cache.getQueryState(RESUMEN)?.isInvalidated).toBe(true);
  });

  it('un reporte cerrado se lee pero no se escribe, y dice cómo corregirlo', async () => {
    await abrir(
      reporteCerrable({ estado: 'FINALIZADO', finalizado: '2026-09-27T10:00:00' }),
    );

    expect(campo('procedimientos').disabled).toBe(true);
    expect(opcionesDeResultado().every((opcion) => opcion.disabled)).toBe(true);
    expect(texto()).toContain('Para corregirlo hay que retirarlo y abrir otro');
    expect(texto()).toContain('cerrado el 2026-09-27');
    expect(
      [...raiz().querySelectorAll('button')].some((b) =>
        (b.textContent ?? '').includes('Cerrar reporte'),
      ),
    ).toBe(false);
  });

  it('un reporte retirado tampoco se escribe', async () => {
    await abrir(reporte({ estadoActivo: false }));

    expect(campo('procedimientos').disabled).toBe(true);
    expect(texto()).toContain('Retirado');
  });

  it('retirar avisa de que no se borra nada y vuelve a la orden', async () => {
    const router = TestBed.inject(Router);
    const viaje = vi.spyOn(router, 'navigate');
    await abrir();

    pulsar('Retirar');
    expect(texto()).toContain('No se borra nada');

    pulsar('Confirmar retiro');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null);
    await asentar(fixture);

    expect(viaje).toHaveBeenCalledWith(['/ordenes', ID_ORDEN]);
    http.match(urlReporte(ID_REPORTE)).forEach((p) => p.flush(reporte()));
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((p) => p.flush(EQUIPOS));
    await asentar(fixture);
  });

  it('sin report.write se lee y no se ofrece ninguna escritura', async () => {
    TestBed.resetTestingModule();
    montar(['report.read', 'equipment.read']);
    await abrir(reporteCerrable());

    expect(campo('procedimientos').disabled).toBe(true);
    expect(
      [...raiz().querySelectorAll('button')].map((b) => (b.textContent ?? '').trim()),
    ).toEqual([]);
  });

  it('un 409 al cerrar se explica con el mensaje del catálogo, no con el del servidor', async () => {
    await abrir(reporteCerrable());
    pulsar('Cerrar reporte');
    pulsar('Confirmar cierre');
    await asentar(fixture);

    http.expectOne(urlCerrarReporte(ID_REPORTE)).flush(
      { code: 'ERR_SERVICE_REPORT_003', message: 'Service report state does not allow it' },
      { status: 409, statusText: 'Conflict' },
    );
    await asentar(fixture);

    expect(texto()).toContain('la verificación completa');
    expect(texto()).not.toContain('Service report state');
  });

  it('una recarga de la caché no pisa lo que se está escribiendo', async () => {
    // Sin la guarda del efecto, cada recarga devolvería el formulario al valor del servidor mientras
    // alguien teclea. Es el mismo defecto que ya apareció editando un cliente.
    //
    // La recarga se provoca invalidando la caché, que es exactamente lo que hace cualquier escritura
    // del módulo. La primera versión de esta prueba se limitaba a llamar a `http.match`, y pasaba en
    // vacío: sin invalidar no hay peticion pendiente que responder, así que el efecto no volvía a
    // correr y la prueba no ejercía nada. Lo delató una mutación -quitar la guarda y verla seguir
    // verde-, que es el tercer caso de este patrón en el proyecto.
    await abrir();
    escribir('diagnostico', 'Lo estoy escribiendo');

    void TestBed.inject(QueryClient).invalidateQueries({ queryKey: ReporteApi.CLAVE });
    await responderA(
      fixture,
      http,
      urlReporte(ID_REPORTE),
      reporte({ diagnostico: 'Lo que tiene el servidor' }),
    );

    expect(campo('diagnostico').value).toBe('Lo estoy escribiendo');
  });
});
