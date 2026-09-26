import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EquiposDeCatalogo } from './equipos';
import { Fabricantes } from './fabricantes';
import { Marcas } from './marcas';
import { Modelos } from './modelos';
import { TiposDeEquipo } from './tipos';

/**
 * El catalogo de equipos, sus cinco piezas en el orden en que se usan.
 *
 * <p><b>El orden de las secciones es la leccion de la pantalla.</b> Registrar un equipo de un cliente
 * exige una cadena: marca y tipo se combinan en un equipo del catalogo; ese equipo con un fabricante
 * forma un modelo; y solo un modelo se puede instalar en un area. Quien llegue sin saberlo descubre la
 * dependencia al encontrarse el desplegable vacio, asi que se recorre de arriba abajo y cada seccion
 * dice para que sirve la siguiente.
 *
 * <p>Es una pagina con cinco componentes y no cinco rutas: las cinco listas se consultan juntas de
 * todos modos —ninguna respuesta trae nombres y hay que cruzarlas para pintar una fila legible—, de
 * modo que separarlas en cinco pantallas costaria las mismas consultas y esconderia la cadena.
 */
@Component({
  selector: 'app-catalogo',
  imports: [Marcas, TiposDeEquipo, Fabricantes, EquiposDeCatalogo, Modelos],
  templateUrl: './catalogo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Catalogo {}
