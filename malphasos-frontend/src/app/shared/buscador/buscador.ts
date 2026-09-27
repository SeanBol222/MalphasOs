import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { anotarEnHistorial, historialDe } from './historial';

/** Una opcion elegible: lo que se guarda y lo que se lee. */
export interface Opcion {
  readonly id: string;
  readonly etiqueta: string;
}

/**
 * Un campo de texto que predice sobre un catalogo largo, en lugar de un desplegable.
 *
 * <p><b>Por que existe.</b> Un {@code select} con 249 paises o 1.103 municipios es inservible: hay que
 * recorrerlo con la vista o adivinar la inicial, y en un telefono se abre una rueda infinita. Se
 * escribe «mede» y aparece Medellin.
 *
 * <p><b>Sin escribir nada solo se ofrece lo ya usado en este navegador</b>, no el catalogo entero:
 * abrir el campo y encontrarse mil opciones no ayuda a nadie. Al escribir, en cambio, se busca en
 * <b>todo</b> el catalogo — si no, el primer cliente de Boyaca no se podria registrar nunca.
 *
 * <p><b>No inventa valores.</b> El valor del formulario es el {@code id} de la opcion elegida, y un
 * texto que no case con ninguna deja el campo vacio y el formulario lo rechaza diciendo por que. Vale
 * igual para un pais -donde el identificador es un UUID- que para una lista cerrada de valores de
 * texto, como las tecnologias autorizadas: ahi el identificador de cada opcion ES su nombre, de modo
 * que se guarda el texto y a la vez no se admite uno que no este en la lista.
 *
 * <p>Hubo un modo libre, que aceptaba cualquier texto y usaba las opciones como sugerencias. <b>Se
 * retiro el 2026-09-26, el mismo dia</b>: la tecnologia predominante era su unico uso y se decidio que
 * fuera una lista cerrada. Un modo sin ningun uso es mantenimiento a cambio de nada.
 *
 * <p>Se compara <b>sin tildes y sin mayusculas</b>: quien escribe «medellin» en un teclado de telefono
 * espera encontrar Medellín, y no hacerlo es la queja mas segura de este tipo de campo.
 *
 * <p>Es un {@code ControlValueAccessor} para que los formularios reactivos lo usen como cualquier otro
 * control, con su {@code formControlName} y sus validadores. Lo contrario habria sido un campo que
 * obliga a tratarlo distinto en cada pantalla.
 */
@Component({
  selector: 'app-buscador',
  templateUrl: './buscador.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Buscador), multi: true },
  ],
})
export class Buscador implements ControlValueAccessor {
  /** El catalogo completo sobre el que se busca. */
  readonly opciones = input.required<readonly Opcion[]>();

  /** Lo que dice la etiqueta del campo. */
  readonly etiqueta = input.required<string>();

  /** El identificador del control en el DOM, que es tambien el `for` de la etiqueta. */
  readonly campo = input.required<string>();

  /**
   * La clave con la que se recuerda lo elegido.
   *
   * <p>Separada de {@code campo} a proposito: el pais de un cliente y el pais de un fabricante son dos
   * campos distintos en dos pantallas distintas, y comparten historial porque son la misma pregunta.
   */
  readonly historial = input.required<string>();

  /** Que decir cuando el catalogo esta vacio o no llego. */
  readonly avisoSinCatalogo = input<string>('');

  /** Si el campo admite quedarse vacio. */
  readonly opcional = input<boolean>(false);

  /** Cuantas sugerencias se ensenan al escribir. Mas no caben sin convertir la lista en otra lista. */
  private static readonly MAXIMO_SUGERENCIAS = 10;

  protected readonly texto = signal('');
  protected readonly abierto = signal(false);
  protected readonly resaltada = signal(-1);
  protected readonly tocado = signal(false);

  /** El identificador elegido, que es lo que ve el formulario. */
  private readonly valor = signal('');

  private escribiendo = false;
  private alCambiar: (valor: string) => void = () => {};
  private alTocar: () => void = () => {};

  constructor() {
    // El texto se deriva del valor mientras nadie escriba. Hace falta porque el catalogo llega despues
    // que el valor: al abrir una pantalla de edicion, el identificador esta antes que la lista en la
    // que buscar su nombre, y sin esto el campo se quedaria en blanco con un valor dentro.
    effect(() => {
      const elegido = this.valor();
      const nombre = this.opciones().find((opcion) => opcion.id === elegido)?.etiqueta ?? '';

      if (!this.escribiendo && nombre !== this.texto()) {
        this.texto.set(nombre);
      }
    });
  }

  /**
   * Lo que se ofrece en cada momento.
   *
   * <p>Sin texto, lo ya usado —y solo lo que siga existiendo en el catalogo, porque una opcion retirada
   * no debe volver por la puerta de atras—. Con texto, el catalogo entero filtrado.
   */
  protected readonly sugerencias = computed<readonly Opcion[]>(() => {
    const escrito = normalizar(this.texto());
    const opciones = this.opciones();

    if (!escrito) {
      const porId = new Map(opciones.map((opcion) => [opcion.id, opcion]));

      return historialDe(this.historial())
        .map((id) => porId.get(id))
        .filter((opcion): opcion is Opcion => !!opcion);
    }

    return opciones
      .filter((opcion) => normalizar(opcion.etiqueta).includes(escrito))
      .slice(0, Buscador.MAXIMO_SUGERENCIAS);
  });

  /** Lo escrito no casa con nada: hay que decirlo, y el formulario no debe dejar guardar. */
  protected readonly sinCoincidencias = computed(
    () => !!this.texto().trim() && this.sugerencias().length === 0,
  );

  /** Se abrio el campo sin haber elegido nunca nada: no hay historial que ofrecer. */
  protected readonly sinHistorial = computed(
    () => !this.texto().trim() && this.sugerencias().length === 0 && this.opciones().length > 0,
  );

  // --- ControlValueAccessor -------------------------------------------------

  writeValue(valor: string | null): void {
    this.escribiendo = false;
    this.valor.set(valor ?? '');
  }

  registerOnChange(fn: (valor: string) => void): void {
    this.alCambiar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  // --- Interaccion ----------------------------------------------------------

  protected escribir(evento: Event): void {
    this.escribiendo = true;
    this.texto.set((evento.target as HTMLInputElement).value);
    this.abierto.set(true);
    this.resaltada.set(-1);

    // Mientras lo escrito no sea exactamente una opcion, el formulario no tiene valor: asi un nombre a
    // medias o inventado no pasa por bueno.
    const exacta = this.opciones().find(
      (opcion) => normalizar(opcion.etiqueta) === normalizar(this.texto()),
    );

    this.fijar(exacta?.id ?? '');
  }

  protected elegir(opcion: Opcion): void {
    this.escribiendo = false;
    this.texto.set(opcion.etiqueta);
    this.fijar(opcion.id);
    anotarEnHistorial(this.historial(), opcion.id);
    this.abierto.set(false);
    this.resaltada.set(-1);
  }

  protected abrir(): void {
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
    this.tocado.set(true);
    this.alTocar();
  }

  protected teclear(evento: KeyboardEvent): void {
    const sugerencias = this.sugerencias();

    if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      // Sin esto, las flechas mueven el cursor dentro del texto en vez de la seleccion.
      evento.preventDefault();
      this.abierto.set(true);

      if (sugerencias.length) {
        const paso = evento.key === 'ArrowDown' ? 1 : -1;
        const siguiente = (this.resaltada() + paso + sugerencias.length) % sugerencias.length;

        this.resaltada.set(siguiente);
      }

      return;
    }

    if (evento.key === 'Enter' && this.abierto() && sugerencias[this.resaltada()]) {
      // Solo se traga el Enter si hay algo resaltado: si no, tiene que enviar el formulario.
      evento.preventDefault();
      this.elegir(sugerencias[this.resaltada()]);

      return;
    }

    if (evento.key === 'Escape') {
      this.abierto.set(false);
      this.resaltada.set(-1);
    }
  }

  private fijar(id: string): void {
    this.valor.set(id);
    this.alCambiar(id);
  }
}

/**
 * Lo que el buscador necesita, sacado de cualquier lista del API.
 *
 * <p>Todas las respuestas de referencia -paises, ciudades, marcas- traen {@code id} y {@code nombre},
 * asi que la conversion es la misma en las seis pantallas que usan este campo y se escribe una vez.
 */
export function opcionesDe(
  lista: readonly { id?: string; nombre?: string }[] | undefined,
): readonly Opcion[] {
  return (lista ?? [])
    .filter((fila) => !!fila.id && !!fila.nombre)
    .map((fila) => ({ id: fila.id!, etiqueta: fila.nombre! }));
}

/** Sin tildes, sin mayusculas y sin espacios de sobra: como lo teclea una persona con prisa. */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}
