import { computed, inject } from '@angular/core';
import { CatalogoApi } from '../equipment/catalogo-api';
import { EquipoApi } from '../equipment/equipo-api';

/**
 * El tipo de un equipo instalado, caminando la cadena del catálogo: unidad → modelo → equipo del
 * catálogo → tipo. Así está construida —«equipo» significa categoría y máquina— y ninguna tabla
 * intermedia guarda un atajo; el backend camina la misma cadena en un solo método por la misma razón.
 * Las cuatro listas van en caché porque otras pantallas ya las piden.
 *
 * <p>Vive aparte desde el 2026-10-16 porque la necesitan dos piezas: la tabla de verificación, para
 * saber qué casillas pintar, y el documento del reporte, para su formato y sus puntos. Se llama en un
 * contexto de inyección, como un {@code inject()}.
 */
export function tipoDelEquipo(idEquipoCliente: () => string | undefined) {
  const catalogo = inject(CatalogoApi);
  const equipos = inject(EquipoApi).listarTodos();
  const modelos = catalogo.listarModelos();
  const equiposDeCatalogo = catalogo.listarEquipos();
  const tipos = catalogo.listarTipos();

  /** El tipo del equipo, o {@code undefined} mientras la cadena no esté completa o si se rompe. */
  const tipo = computed(() => {
    const unidad = (equipos.data() ?? []).find((equipo) => equipo.id === idEquipoCliente());
    const modelo = (modelos.data() ?? []).find((m) => m.id === unidad?.idModelo);
    const delCatalogo = (equiposDeCatalogo.data() ?? []).find((e) => e.id === modelo?.idEquipo);

    return (tipos.data() ?? []).find((t) => t.id === delCatalogo?.idTipoEquipo);
  });

  const cargando = computed(
    () =>
      equipos.isPending() ||
      modelos.isPending() ||
      equiposDeCatalogo.isPending() ||
      tipos.isPending(),
  );

  return { tipo, cargando };
}
