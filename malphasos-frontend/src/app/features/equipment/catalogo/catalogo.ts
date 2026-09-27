import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NAVEGACION } from '../../../core/navegacion';

/**
 * El sitio donde viven las cinco piezas del catalogo, con una a la vista cada vez.
 *
 * <p><b>Antes eran las cinco en la misma pagina</b>, una debajo de otra, y con treinta marcas
 * registradas llegar a los fabricantes eran cuatro pantallas de desplazamiento. Ahora cada pieza tiene
 * su propia ruta —{@code /catalogo/marcas}, {@code /catalogo/fabricantes}—, de modo que se puede
 * enlazar, recargar y volver con el boton de atras, tres cosas que unas pestanas con estado interno no
 * dan.
 *
 * <p>La subnavegacion se deriva de {@link NAVEGACION}, la misma lista de la que salen el menu y las
 * rutas: escribirla aparte seria una segunda lista que se desincroniza, y este proyecto ya tiene
 * precedentes anotados de eso.
 *
 * <p>Esta pantalla no consulta nada: cada pieza se trae lo suyo. Entrar en {@code /catalogo} redirige a
 * la primera, porque una pagina que solo tiene un menu no es una pagina.
 */
@Component({
  selector: 'app-catalogo',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './catalogo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Catalogo {
  /** Las cinco piezas, tal como las declara la navegacion. */
  protected readonly piezas =
    NAVEGACION.find((entrada) => entrada.ruta === 'catalogo')?.hijos ?? [];
}
