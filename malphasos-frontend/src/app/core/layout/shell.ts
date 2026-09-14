import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NAVEGACION } from '../navegacion';

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
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly navegacion = NAVEGACION;
}
