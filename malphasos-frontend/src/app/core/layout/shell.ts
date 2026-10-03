import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { EntradaDeNavegacion, NAVEGACION } from '../navegacion';
import { Sesion } from '../auth/sesion';

/**
 * El armazon de la aplicacion: cabecera, navegacion principal y el contenido.
 *
 * <p>Envuelve solo a las rutas de dentro de la sesion. Las publicas -el inicio
 * de sesion- cuelgan de la raiz y no lo arrastran, que es la razon de que este
 * armazon no viva en el componente raiz.
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
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly sesion = inject(Sesion);

  /**
   * Los destinos que esta sesion puede usar de verdad.
   *
   * <p>Quien trae {@code admin.full} los ve todos: la expansion la resuelve {@code Sesion.puede}, en un
   * solo sitio, igual que en el backend.
   */
  protected readonly navegacion = computed(() =>
    NAVEGACION.filter((entrada) => !entrada.autoridad || this.sesion.puede(entrada.autoridad)),
  );

  /** La entrada cuyo desplegable esta abierto, si hay alguno. Solo uno a la vez. */
  protected readonly desplegada = signal<string | null>(null);

  protected alternar(entrada: EntradaDeNavegacion): void {
    this.desplegada.update((abierta) => (abierta === entrada.ruta ? null : entrada.ruta));
  }

  protected cerrar(): void {
    this.desplegada.set(null);
  }
}
