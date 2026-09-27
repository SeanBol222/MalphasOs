import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AreaApi } from '../../client/area-api';
import { CatalogoApi } from '../catalogo-api';
import { EquipoApi } from '../equipo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Todos los equipos de cliente registrados, que es lo que se viene a ver aqui.
 *
 * <p><b>Sustituye al catalogo como entrada del menu</b>, decidido el 2026-09-26: lo que se consulta a
 * diario es «que equipos hay», no «que marcas existen». El catalogo sigue teniendo su propia entrada,
 * porque administrarlo es una tarea distinta y no debe exigir empezar a registrar un equipo.
 *
 * <p>Cada fila necesita dos resoluciones que el API no da: el <b>modelo</b> en palabras —cruzando
 * cuatro listas del catalogo— y el <b>nombre del area</b>, que cuesta una peticion por area distinta
 * porque no existe ni un listado global de areas ni una consulta por lote. Ver [[deuda-tecnica-y-riesgos]].
 */
@Component({
  selector: 'app-lista-equipos',
  imports: [RouterLink],
  templateUrl: './lista-equipos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaEquipos {
  private readonly catalogo = inject(CatalogoApi);
  private readonly sesion = inject(Sesion);

  protected readonly equipos = inject(EquipoApi).listarTodos();

  private readonly modelos = this.catalogo.listarModelos();
  private readonly equiposDeCatalogo = this.catalogo.listarEquipos();
  private readonly tipos = this.catalogo.listarTipos();
  private readonly marcas = this.catalogo.listarMarcas();
  private readonly fabricantes = this.catalogo.listarFabricantes();

  /** Las areas que aparecen de verdad en la lista, sin repetir. */
  private readonly idsDeAreas = computed(() => [
    ...new Set((this.equipos.data() ?? []).map((equipo) => equipo.idAreaServicio!).filter(Boolean)),
  ]);

  private readonly areas = inject(AreaApi).variasPorId(this.idsDeAreas);

  protected readonly puedeRegistrar = computed(() => this.sesion.puede('equipment.assign'));

  /** Un modelo en palabras: «tipo · marca · fabricante». Cuatro listas para una etiqueta. */
  private readonly modelosLegibles = computed(() => {
    const tipos = new Map((this.tipos.data() ?? []).map((t) => [t.id, t.nombre]));
    const marcas = new Map((this.marcas.data() ?? []).map((m) => [m.id, m.nombre]));
    const fabricantes = new Map((this.fabricantes.data() ?? []).map((f) => [f.id, f.nombre]));
    const equipos = new Map(
      (this.equiposDeCatalogo.data() ?? []).map((equipo) => [
        equipo.id,
        `${tipos.get(equipo.idTipoEquipo!) ?? 'Tipo no disponible'} · ${
          marcas.get(equipo.idMarca!) ?? 'Marca no disponible'
        }`,
      ]),
    );

    return new Map(
      (this.modelos.data() ?? []).map((modelo) => [
        modelo.id,
        `${equipos.get(modelo.idEquipo!) ?? 'Equipo no disponible'} · ${
          fabricantes.get(modelo.idFabricante!) ?? 'Fabricante no disponible'
        }`,
      ]),
    );
  });

  protected readonly filas = computed(() => {
    const modelos = this.modelosLegibles();
    const areas = new Map((this.areas.data() ?? []).map((area) => [area.id, area.nombre]));

    return (this.equipos.data() ?? []).map((equipo) => ({
      id: equipo.id!,
      serie: equipo.serie,
      numeroInventario: equipo.numeroInventario,
      estadoActivo: equipo.estadoActivo,
      idArea: equipo.idAreaServicio,
      modelo: modelos.get(equipo.idModelo!) ?? 'Modelo no disponible',
      area: areas.get(equipo.idAreaServicio!) ?? 'Área no disponible',
    }));
  });

  protected readonly hayError = computed(() => this.equipos.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.equipos.error()));
}
