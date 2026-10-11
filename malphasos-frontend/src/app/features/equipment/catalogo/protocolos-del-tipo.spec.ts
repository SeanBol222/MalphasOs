import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { CdkDropList } from '@angular/cdk/drag-drop';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import {
  asentar,
  escribirEnBuscador,
  responderA,
  sugerenciasDe,
} from '../../../../testing/pantalla';
import { PROTOCOLOS, URL_PROTOCOLOS } from '../../../../testing/catalogo';
import { AsignacionDeProtocolos } from '../../../core/api/tipos';
import { ProtocolosDelTipo } from './protocolos-del-tipo';

/**
 * El bloque de protocolos del tipo de equipo, del 2026-10-10.
 *
 * <p>Decisiones del usuario que fija: la prioridad la decide quien ordena la lista —arrastrando o con
 * flechas— y nunca el programa; se eligen entre los que existen; y crear uno nuevo avisa de los
 * parecidos para que no sea facil repetirlos.
 */
describe('Protocolos del tipo de equipo', () => {
  let fixture: ComponentFixture<ProtocolosDelTipo>;
  let http: HttpTestingController;
  let desinstalarAlmacenamiento: () => void;
  let emitido: AsignacionDeProtocolos | undefined;

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const ordenEmitido = () => emitido?.protocolos?.map((p) => p.idProtocolo);
  const nombresEnPantalla = () =>
    [...raiz().querySelectorAll('li[data-protocolo] p.font-bold')].map((p) =>
      p.textContent?.trim(),
    );
  const boton = (etiqueta: string) =>
    [...raiz().querySelectorAll('button')].find(
      (b) => b.getAttribute('aria-label') === etiqueta || b.textContent?.trim() === etiqueta,
    ) as HTMLButtonElement;

  async function montar(inicial: object[] | null = null): Promise<void> {
    TestBed.configureTestingModule({ providers: [...proveerApiSimulado()] });
    fixture = TestBed.createComponent(ProtocolosDelTipo);
    http = TestBed.inject(HttpTestingController);
    fixture.componentInstance.cambio.subscribe((valor) => (emitido = valor));
    if (inicial) {
      fixture.componentRef.setInput('inicial', inicial);
    }
    fixture.detectChanges();
    await responderA(fixture, http, URL_PROTOCOLOS, PROTOCOLOS);
  }

  /**
   * Escribe parte del nombre y elige la sugerencia. No con {@code elegirEnBuscador}: ese escribe la
   * etiqueta entera, y con el texto exacto el buscador elige solo; el protocolo se agrega, sale de las
   * sugerencias, y el ayudante no lo encuentra.
   */
  async function agregarDesdeElBuscador(parte: string, etiqueta: string): Promise<void> {
    await escribirEnBuscador(fixture, 'agregarProtocolo', parte);
    const opcion = [
      ...raiz().querySelectorAll<HTMLElement>('#agregarProtocolo-lista [role="option"]'),
    ].find((candidata) => candidata.textContent?.trim() === etiqueta);
    if (!opcion) {
      throw new Error(`"${etiqueta}" no se ofrece al escribir "${parte}"`);
    }
    opcion.dispatchEvent(new MouseEvent('mousedown'));
    await asentar(fixture);
  }

  async function pulsar(etiqueta: string): Promise<void> {
    boton(etiqueta).click();
    await asentar(fixture);
  }

  beforeEach(() => {
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    emitido = undefined;
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      desinstalarAlmacenamiento();
    }
  });

  describe('Elegir de los que existen', () => {
    it('sin ninguno elegido lo dice, y emite la lista vacía', async () => {
      await montar();

      expect(texto()).toContain('Sin protocolos');
      expect(emitido).toEqual({ protocolos: [] });
    });

    it('el buscador ofrece solo los vigentes, y elegir uno lo agrega con la prioridad 1', async () => {
      await montar();
      await escribirEnBuscador(fixture, 'agregarProtocolo', 'o');

      // El retirado no se ofrece: no se puede asignar.
      expect(sugerenciasDe(fixture, 'agregarProtocolo')).not.toContain('Protocolo viejo');

      await agregarDesdeElBuscador('limp', 'Limpieza y desinfección');

      expect(nombresEnPantalla()).toEqual(['Limpieza y desinfección']);
      expect(ordenEmitido()).toEqual(['pr1']);
    });

    it('con todos elegidos no dice que el catálogo está vacío, porque no lo está', async () => {
      await montar([
        {
          idProtocolo: 'pr1',
          nombre: 'Limpieza y desinfección',
          descripcion: 'Paño.',
          prioridad: 1,
        },
        {
          idProtocolo: 'pr2',
          nombre: 'Verificación eléctrica',
          descripcion: 'Multímetro.',
          prioridad: 2,
        },
      ]);
      await escribirEnBuscador(fixture, 'agregarProtocolo', '');

      expect(texto()).not.toContain('Todavía no hay protocolos');
      expect(texto()).toContain('Ya están en la lista todos los protocolos');
    });

    it('uno ya elegido no se vuelve a ofrecer', async () => {
      await montar();
      await agregarDesdeElBuscador('limp', 'Limpieza y desinfección');
      await escribirEnBuscador(fixture, 'agregarProtocolo', 'i');

      expect(sugerenciasDe(fixture, 'agregarProtocolo')).not.toContain('Limpieza y desinfección');
    });
  });

  describe('El orden es la prioridad, y lo decide quien ordena', () => {
    const DOS = [
      { idProtocolo: 'pr1', nombre: 'Limpieza y desinfección', descripcion: 'Paño.', prioridad: 1 },
      {
        idProtocolo: 'pr2',
        nombre: 'Verificación eléctrica',
        descripcion: 'Multímetro.',
        prioridad: 2,
      },
    ];

    it('llega con los que el tipo ya tiene, en su orden', async () => {
      await montar(DOS);

      expect(nombresEnPantalla()).toEqual(['Limpieza y desinfección', 'Verificación eléctrica']);
      expect(ordenEmitido()).toEqual(['pr1', 'pr2']);
    });

    it('bajar el primero lo deja segundo, y lo anuncia', async () => {
      await montar(DOS);
      await pulsar('Bajar Limpieza y desinfección');

      expect(ordenEmitido()).toEqual(['pr2', 'pr1']);
      expect(raiz().querySelector('[aria-live="polite"]')?.textContent).toContain('prioridad 2');
    });

    it('el primero no se sube y el último no se baja', async () => {
      await montar(DOS);

      expect(boton('Subir Limpieza y desinfección').disabled).toBe(true);
      expect(boton('Bajar Verificación eléctrica').disabled).toBe(true);
    });

    it('arrastrar reordena igual que las flechas', async () => {
      await montar(DOS);
      fixture.debugElement
        .query(By.directive(CdkDropList))
        .triggerEventHandler('cdkDropListDropped', { previousIndex: 1, currentIndex: 0 });
      await asentar(fixture);

      expect(ordenEmitido()).toEqual(['pr2', 'pr1']);
    });

    it('quitar uno lo saca de la lista', async () => {
      await montar(DOS);
      await pulsar('Quitar Limpieza y desinfección');

      expect(ordenEmitido()).toEqual(['pr2']);
    });

    it('la indicación viaja solo si tiene texto', async () => {
      await montar(DOS);
      const campo = raiz().querySelector<HTMLInputElement>('#indicacion-pr2')!;
      campo.value = '  Con guantes ';
      campo.dispatchEvent(new Event('input'));
      await asentar(fixture);

      expect(emitido?.protocolos).toEqual([
        { idProtocolo: 'pr1' },
        { idProtocolo: 'pr2', indicacion: 'Con guantes' },
      ]);
    });
  });

  describe('Crear uno nuevo sin repetir', () => {
    async function abrirPanelYEscribir(nombre: string): Promise<void> {
      await pulsar('Crear un protocolo nuevo');
      const campo = raiz().querySelector<HTMLInputElement>('#nombre-protocolo')!;
      campo.value = nombre;
      campo.dispatchEvent(new Event('input'));
      await asentar(fixture);
    }

    it('avisa de los parecidos mientras se escribe, sin tildes ni mayúsculas', async () => {
      await montar();
      await abrirPanelYEscribir('LIMPIEZA general');

      expect(texto()).toContain('Ya existen protocolos parecidos');
      expect(texto()).toContain('Limpieza y desinfección');
      // El retirado no cuenta como parecido: no se puede usar.
      expect(texto()).not.toContain('Protocolo viejo');
    });

    it('con el mismo nombre no deja crearlo y ofrece usar el que existe', async () => {
      await montar();
      await abrirPanelYEscribir('limpieza y desinfeccion');

      expect(texto()).toContain('Ya existe un protocolo con este nombre');
      expect(boton('Crear y agregar').disabled).toBe(true);

      await pulsar('Usar este');

      expect(ordenEmitido()).toEqual(['pr1']);
      expect(raiz().querySelector('#nombre-protocolo')).toBeNull();
    });

    it('crear manda el protocolo al catálogo y lo agrega a la lista', async () => {
      await montar();
      await abrirPanelYEscribir('Calibración de celdas');
      const descripcion = raiz().querySelector<HTMLTextAreaElement>('#descripcion-protocolo')!;
      descripcion.value = 'Con pesas patrón.';
      descripcion.dispatchEvent(new Event('input'));
      await pulsar('Crear y agregar');

      const alta = http.expectOne({ method: 'POST', url: URL_PROTOCOLOS });
      expect(alta.request.body).toEqual({
        nombre: 'Calibración de celdas',
        descripcion: 'Con pesas patrón.',
      });
      alta.flush({
        id: 'pr9',
        nombre: 'Calibración de celdas',
        descripcion: 'Con pesas patrón.',
        estadoActivo: true,
      });
      await asentar(fixture);
      // El alta invalida el catalogo y lo vuelve a pedir.
      http.match(URL_PROTOCOLOS).forEach((p) => p.flush(PROTOCOLOS));
      await asentar(fixture);

      expect(ordenEmitido()).toEqual(['pr9']);
    });

    it('sin descripción no llega al servidor', async () => {
      await montar();
      await abrirPanelYEscribir('Calibración de celdas');
      await pulsar('Crear y agregar');

      http.expectNone({ method: 'POST', url: URL_PROTOCOLOS });
    });
  });
});
