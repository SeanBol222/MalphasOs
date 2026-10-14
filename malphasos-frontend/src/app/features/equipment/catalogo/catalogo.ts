import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NAVEGACION } from '../../../core/navegacion';
import { CatalogoApi } from '../catalogo-api';

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
 * <p>Entrar en {@code /catalogo} redirige a la primera, porque una pagina que solo tiene un menu no es
 * una pagina.
 *
 * <p><b>Desde el rediseño del 2026-10-11 cada pestaña dice cuántos hay</b> —la maqueta «A-Catalogo»—,
 * y para eso esta pantalla pide las seis listas. No es una consulta de más: son las mismas claves que
 * cada pieza pide al abrirse, de modo que la pieza encuentra su lista ya en caché. Se cuentan solo las
 * vigentes: lo retirado no está en el catálogo de hoy.
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

  private readonly api = inject(CatalogoApi);
  private readonly listas = {
    tipos: this.api.listarTipos(),
    marcas: this.api.listarMarcas(),
    fabricantes: this.api.listarFabricantes(),
    equipos: this.api.listarEquipos(),
    modelos: this.api.listarModelos(),
    protocolos: this.api.listarProtocolos(),
    patrones: this.api.listarPatrones(),
  } as const;

  /** Cuántas vigentes hay de cada pieza, por su ruta. Vacío mientras la lista no ha llegado. */
  protected readonly cuantos = computed(
    () =>
      Object.fromEntries(
        Object.entries(this.listas).map(([ruta, consulta]) => {
          const datos = consulta.data() as readonly { estadoActivo?: boolean }[] | undefined;

          return [ruta, datos ? String(datos.filter((d) => d.estadoActivo).length) : ''];
        }),
      ) as Record<string, string>,
  );
}
