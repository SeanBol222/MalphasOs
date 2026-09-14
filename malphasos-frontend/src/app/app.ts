import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Raiz de la aplicacion. No pinta nada por si misma: solo aloja la ruta activa.
 *
 * El armazon -barra superior, navegacion, region de anuncios- vive en el
 * componente de disposicion, no aqui, para que las rutas publicas como el
 * inicio de sesion no lo arrastren.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
