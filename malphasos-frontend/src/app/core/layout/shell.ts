import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { EntradaDeNavegacion, GRUPOS_DE_NAVEGACION, NAVEGACION } from '../navegacion';
import { Sesion } from '../auth/sesion';
import { ETIQUETA_DE_TIPO_DE_PERSONA } from '../api/tipos';
import { PersonaApi } from '../../features/person/persona-api';
import { VentanaDeUsuario } from '../../features/person/ventana/ventana-de-usuario';

/**
 * Los trazos del icono de cada destino de primer nivel, en una reticula de 24.
 *
 * <p>Viven aqui y no en {@link NAVEGACION} porque son de este armazon: la lista de navegacion la leen
 * tambien las rutas, que no pintan nada.
 */
const ICONOS: Readonly<Record<string, readonly string[]>> = {
  inicio: ['M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'],
  ordenes: ['M4 3h16v18H4z', 'M8 7h8M8 11h8M8 15h5'],
  equipos: ['M3 6h18v12H3z', 'M7 10h4M7 14h2M15 12h2'],
  clientes: ['M4 21V5l8-2 8 2v16', 'M9 21v-5h6v5M8 9h2M14 9h2M8 13h2M14 13h2'],
  personas: [
    'M5 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0',
    'M2 21c0-4 3-6 7-6s7 2 7 6M17 4a4 4 0 0 1 0 8M22 21c0-3-2-5-4-6',
  ],
  catalogo: ['M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z'],
  // Una flecha que entra en una bandeja: los datos llegan de fuera (2026-11-08).
  importar: ['M12 3v12M7 10l5 5 5-5', 'M4 17v4h16v-4'],
};

/**
 * El armazon de la aplicacion: la barra lateral con la navegacion principal, y el contenido.
 *
 * <p>Envuelve solo a las rutas de dentro de la sesion. Las publicas -el inicio
 * de sesion- cuelgan de la raiz y no lo arrastran, que es la razon de que este
 * armazon no viva en el componente raiz.
 *
 * <p><b>Es una barra lateral desde el 2026-10-10</b>, la «sala de control» que el usuario eligio entre
 * tres propuestas. Hasta entonces el menu era una fila bajo la cabecera, y con seis destinos se
 * desbordaba en un telefono: estaba anotado como deuda. Ahora en un telefono la barra se esconde tras
 * un boton y se abre a pantalla completa. Y trae algo que el armazon anterior no tenia: <b>cerrar
 * sesion</b>, que no existia en ninguna pantalla.
 *
 * <p>Tres cosas que no son decorativas y que WCAG 2.1 AA exige: el enlace de
 * salto, para no obligar a tabular la navegacion entera en cada pagina; los
 * puntos de referencia {@code header}, {@code nav} y {@code main}, que son como
 * un lector de pantalla se mueve; y {@code aria-current} en el destino activo,
 * porque el subrayado de color no le dice nada a quien no ve.
 *
 * <p>Lo ultimo lo pone {@code ariaCurrentWhenActive} de {@code RouterLinkActive}
 * y no un calculo propio. Hubo uno, con una senal que leia {@code router.url}:
 * sobraba y ademas fallaba, porque al construirse el componente la navegacion
 * todavia no habia terminado.
 *
 * <p><b>El menu no pinta lo que la sesion no puede usar</b> (2026-10-02). La autoridad la declara la
 * propia entrada de navegacion, que es la misma de la que sale el guard de la ruta: ocultar sin
 * proteger dejaria el destino alcanzable escribiendo la direccion, y proteger sin ocultar ofreceria un
 * enlace que acaba en «sin permiso». Lo hizo necesario «Personas»: es el primer destino que un grupo
 * legitimo del realm no puede usar, porque {@code clients} no tiene {@code person.read}.
 *
 * <p><b>Una entrada con hijas se despliega</b> en lugar de enlazar: el catalogo de equipos tiene cinco
 * piezas y «catalogo» no es una pantalla, es el sitio donde estan. El desplegable es un boton con
 * {@code aria-expanded} y no un menu de CSS que se abre al pasar el raton: con el raton por encima no se
 * puede navegar con el teclado, y en un telefono no hay raton.
 *
 * <p><b>El nombre al pie abre la ventana de quien tiene la sesion</b> (2026-10-17): sus datos, su firma
 * digital y cerrar sesion. Sustituye al enlace «Mi firma» y al boton de salir que vivian sueltos junto
 * al nombre. La ventana es de {@code features/person} porque lee de ese modulo; el armazon solo decide
 * cuando se abre y adonde vuelve el foco.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgTemplateOutlet, VentanaDeUsuario],
  templateUrl: './shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly sesion = inject(Sesion);

  /**
   * Los destinos que esta sesion puede usar de verdad.
   *
   * <p>Quien trae {@code admin.full} los ve todos: la expansion la resuelve {@code Sesion.puede}, en un
   * solo sitio, igual que en el backend.
   */
  protected readonly navegacion = computed(() =>
    NAVEGACION.filter((entrada) => !entrada.autoridad || this.sesion.puede(entrada.autoridad)),
  );

  /**
   * Los destinos repartidos en los bloques del menu. Un bloque que se queda sin destinos no se pinta:
   * un rotulo «Administracion» sin nada debajo es ruido para quien no administra.
   */
  protected readonly grupos = computed(() =>
    GRUPOS_DE_NAVEGACION.map((grupo) => ({
      ...grupo,
      entradas: this.navegacion().filter((entrada) => (entrada.grupo ?? 'operacion') === grupo.id),
    })).filter((grupo) => grupo.entradas.length > 0),
  );

  /** La ficha de quien tiene la sesion: la misma consulta que la ventana, que la comparte por clave. */
  private readonly persona = inject(PersonaApi).yo();

  /** El tipo de persona bajo el nombre; vacio para una cuenta sin ficha. */
  protected readonly rol = computed(() => {
    const tipo = this.persona.data()?.tipoPersona;

    return tipo ? ETIQUETA_DE_TIPO_DE_PERSONA[tipo] : '';
  });

  /** Si la ventana de la sesion esta abierta. */
  protected readonly ventanaAbierta = signal(false);

  private readonly botonDeUsuario = viewChild.required<ElementRef<HTMLElement>>('botonDeUsuario');

  /** Las iniciales del nombre de la sesion, para el cuadro junto a el. */
  protected readonly iniciales = computed(() =>
    String(this.sesion.nombre())
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((palabra: string) => palabra[0]!.toUpperCase())
      .join(''),
  );

  /** La entrada cuyo desplegable esta abierto, si hay alguno. Solo uno a la vez. */
  protected readonly desplegada = signal<string | null>(null);

  /** Si el menu esta abierto en un telefono. En escritorio la barra se ve siempre. */
  protected readonly menuAbierto = signal(false);

  protected alternar(entrada: EntradaDeNavegacion): void {
    this.desplegada.update((abierta) => (abierta === entrada.ruta ? null : entrada.ruta));
  }

  protected cerrar(): void {
    this.desplegada.set(null);
  }

  protected alternarMenu(): void {
    this.menuAbierto.update((abierto) => !abierto);
  }

  /** Al elegir un destino el menu del telefono se cierra: se abrio para ir a algun sitio. */
  protected cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  protected alternarVentana(): void {
    this.ventanaAbierta.update((abierta) => !abierta);
  }

  /** Al cerrarla el foco vuelve al nombre, que es de donde salio: sin eso se pierde en el documento. */
  protected cerrarVentana(): void {
    this.ventanaAbierta.set(false);
    this.botonDeUsuario().nativeElement.focus();
  }

  protected iconoDe(ruta: string): readonly string[] {
    return ICONOS[ruta] ?? [];
  }
}
