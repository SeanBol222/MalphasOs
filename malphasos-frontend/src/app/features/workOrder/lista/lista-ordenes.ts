import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClienteApi } from '../../client/cliente-api';
import { SedeApi } from '../../client/sede-api';
import { nombreCompleto, PersonaApi } from '../../person/persona-api';
import { ReporteApi } from '../../report/reporte-api';
import { OrdenApi } from '../orden-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_ESTADO,
  ETIQUETA_DE_PERIODICIDAD,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
  OrdenDeTrabajo,
} from '../../../core/api/tipos';
import { desdeIso, fechaCorta } from '../../../core/fechas';
import { normalizar } from '../../../core/texto';
import { traducirError } from '../../../core/errores/traducir';

/** Lo que dice cada botón del filtro de estado. */
type Filtro = 'POR_HACER' | 'TODAS' | 'EJECUTADAS' | 'ANULADAS';

const FILTROS: readonly { clave: Filtro; etiqueta: string }[] = [
  { clave: 'TODAS', etiqueta: 'Todas' },
  { clave: 'POR_HACER', etiqueta: 'Por hacer' },
  { clave: 'EJECUTADAS', etiqueta: 'Ejecutadas' },
  { clave: 'ANULADAS', etiqueta: 'Anuladas' },
];

/** «Por hacer» junta las creadas y las que están en ejecución: lo que todavía pide trabajo. */
function cumple(orden: OrdenDeTrabajo, filtro: Filtro): boolean {
  switch (filtro) {
    case 'TODAS':
      return true;
    case 'ANULADAS':
      return !orden.estadoActivo;
    case 'EJECUTADAS':
      return !!orden.estadoActivo && orden.estadoEjecucion === 'EJECUTADA';
    case 'POR_HACER':
      return !!orden.estadoActivo && orden.estadoEjecucion !== 'EJECUTADA';
  }
}

const DIA_DE_LA_SEMANA = new Intl.DateTimeFormat('es-CO', { weekday: 'long' });

/**
 * Las órdenes de trabajo: lo que hay que hacer, lo que se está haciendo y lo que falta para darlo por
 * hecho.
 *
 * <p><b>Rehecha el 2026-10-16 sobre la maqueta «A-Órdenes»</b>, aprobada ese día. Abre en «Por hacer»
 * —creadas y en ejecución—, que es la pregunta con la que se llega, y las ordena de la más próxima a la
 * más lejana; las ya hechas o anuladas, de la más reciente hacia atrás.
 *
 * <p><b>Cada fila dice cuánto falta para ejecutarla</b>: «3 de 5 cerrados». Desde el 2026-10-14 una
 * orden no se ejecuta con reportes abiertos, y esa columna es la misma cuenta. La compila el servidor
 * en una consulta para todas —{@code GET /reports/progress}—, porque pedir los reportes orden por orden
 * eran N consultas para pintar una tabla.
 *
 * <p>Cliente, sede e ingeniero llegan como identificadores y se cruzan con sus listas, como en el resto
 * del frontend. El ingeniero exige {@code person.read}, que el grupo de clientes no tiene: sin él la
 * columna y su filtro no se pintan, en lugar de pintarse vacíos.
 */
@Component({
  selector: 'app-lista-ordenes',
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './lista-ordenes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaOrdenes {
  private readonly sesion = inject(Sesion);

  protected readonly ordenes = inject(OrdenApi).listar();
  private readonly clientes = inject(ClienteApi).listar();

  protected readonly veIngenieros = computed(() => this.sesion.puede('person.read'));
  private readonly leeReportes = computed(() => this.sesion.puede('report.read'));
  private readonly personas = inject(PersonaApi).listar(this.veIngenieros);
  private readonly avance = inject(ReporteApi).avancePorOrden(this.leeReportes);

  /** Las sedes que aparecen de verdad en la lista, sin repetir. */
  private readonly idsDeSedes = computed(() => [
    ...new Set((this.ordenes.data() ?? []).map((orden) => orden.idSede!).filter(Boolean)),
  ]);

  private readonly sedes = inject(SedeApi).variasPorId(this.idsDeSedes);

  protected readonly puedeProgramar = computed(() => this.sesion.puede('work-order.write'));

  protected readonly FILTROS = FILTROS;
  protected readonly filtro = signal<Filtro>('POR_HACER');
  protected readonly buscador = new FormControl('', { nonNullable: true });
  protected readonly cliente = new FormControl('', { nonNullable: true });
  protected readonly ingeniero = new FormControl('', { nonNullable: true });
  private readonly texto = toSignal(this.buscador.valueChanges, { initialValue: '' });
  private readonly idCliente = toSignal(this.cliente.valueChanges, { initialValue: '' });
  private readonly idIngeniero = toSignal(this.ingeniero.valueChanges, { initialValue: '' });

  /** Valor del filtro de ingeniero que pide las órdenes sin nadie asignado. */
  protected readonly SIN_ASIGNAR = 'sin-asignar';

  private readonly nombresDeClientes = computed(
    () => new Map((this.clientes.data() ?? []).map((c) => [c.id!, c.razonSocial ?? ''])),
  );
  private readonly nombresDeSedes = computed(
    () => new Map((this.sedes.data() ?? []).map((s) => [s.id!, s.nombre ?? ''])),
  );
  private readonly nombresDePersonas = computed(
    () => new Map((this.personas.data() ?? []).map((p) => [p.identificador!, nombreCompleto(p)])),
  );

  /** Los clientes que tienen alguna orden, por nombre: ofrecer los demás sería ofrecer una lista vacía. */
  protected readonly opcionesDeCliente = computed(() =>
    this.opciones(
      (this.ordenes.data() ?? []).map((o) => o.idCliente),
      this.nombresDeClientes(),
    ),
  );

  /**
   * Los ingenieros que tienen alguna orden. Dos personas pueden llamarse igual —en desarrollo hay dos
   * «Alan Turing», una retirada— y entonces la opción lleva la cédula, o el filtro ofrecería dos
   * nombres idénticos sin forma de saber cuál es cuál.
   */
  protected readonly opcionesDeIngeniero = computed(() => {
    const cedulas = new Map(
      (this.personas.data() ?? []).map((p) => [p.identificador!, p.cedula ?? '']),
    );
    const opciones = this.opciones(
      (this.ordenes.data() ?? []).map((o) => o.idIngeniero),
      this.nombresDePersonas(),
    );
    const repetidos = new Set(
      opciones.map((o) => o.nombre).filter((nombre, i, todos) => todos.indexOf(nombre) !== i),
    );

    return opciones.map((o) =>
      repetidos.has(o.nombre) ? { ...o, nombre: `${o.nombre} · ${cedulas.get(o.id)}` } : o,
    );
  });

  /** Las órdenes que pasan el buscador, el cliente y el ingeniero; el estado se aplica después. */
  private readonly candidatas = computed(() => {
    const buscado = normalizar(this.texto());
    const idCliente = this.idCliente();
    const idIngeniero = this.idIngeniero();
    const clientes = this.nombresDeClientes();
    const sedes = this.nombresDeSedes();

    return (this.ordenes.data() ?? []).filter(
      (orden) =>
        (!idCliente || orden.idCliente === idCliente) &&
        (!idIngeniero ||
          (idIngeniero === this.SIN_ASIGNAR
            ? !orden.idIngeniero
            : orden.idIngeniero === idIngeniero)) &&
        (!buscado ||
          normalizar(clientes.get(orden.idCliente!) ?? '').includes(buscado) ||
          normalizar(sedes.get(orden.idSede!) ?? '').includes(buscado)),
    );
  });

  /** Cuántas hay en cada botón, con los demás filtros puestos: es lo que se vería al pulsarlo. */
  protected readonly cuantas = computed(
    () =>
      Object.fromEntries(
        FILTROS.map((f) => [f.clave, this.candidatas().filter((o) => cumple(o, f.clave)).length]),
      ) as Record<Filtro, number>,
  );

  protected readonly filas = computed(() => {
    const filtro = this.filtro();
    const avance = new Map((this.avance.data() ?? []).map((a) => [a.idOrdenTrabajo!, a]));
    const clientes = this.nombresDeClientes();
    const sedes = this.nombresDeSedes();
    const personas = this.nombresDePersonas();
    // Lo que falta hacer, de lo más próximo a lo más lejano; lo hecho, de lo más reciente hacia atrás.
    const sentido = filtro === 'POR_HACER' ? 1 : -1;

    return this.candidatas()
      .filter((orden) => cumple(orden, filtro))
      .sort(
        (a, b) => sentido * (a.fechaMantenimiento ?? '').localeCompare(b.fechaMantenimiento ?? ''),
      )
      .map((orden) => {
        const equipos = orden.equipos?.length ?? 0;
        const cerrados = Math.min(Number(avance.get(orden.id!)?.cerrados ?? 0), equipos);
        const estado = orden.estadoEjecucion;

        return {
          id: orden.id!,
          fecha: fechaCorta(orden.fechaMantenimiento),
          dia: orden.fechaMantenimiento
            ? DIA_DE_LA_SEMANA.format(desdeIso(orden.fechaMantenimiento))
            : '',
          cliente: clientes.get(orden.idCliente!) ?? 'Cliente no disponible',
          sede: sedes.get(orden.idSede!) ?? 'Sede no disponible',
          servicio: orden.tipoServicio ? ETIQUETA_DE_TIPO_DE_SERVICIO[orden.tipoServicio] : '',
          cada: orden.periodicidad ? ETIQUETA_DE_PERIODICIDAD[orden.periodicidad] : '',
          ingeniero: orden.idIngeniero
            ? (personas.get(orden.idIngeniero) ?? 'No disponible')
            : 'Sin asignar',
          sinIngeniero: !orden.idIngeniero,
          reportes:
            equipos === 0
              ? 'Sin equipos'
              : estado === 'CREADA'
                ? `Sin empezar · ${equipos} ${equipos === 1 ? 'equipo' : 'equipos'}`
                : `${cerrados} de ${equipos} cerrados`,
          sinEquipos: equipos === 0,
          avance: equipos ? Math.round((cerrados / equipos) * 100) : 0,
          // La barra solo dice algo cuando la orden ha empezado: antes, todo es cero por definicion.
          conBarra: equipos > 0 && estado !== 'CREADA',
          estado: orden.estadoActivo ? (estado ? ETIQUETA_DE_ESTADO[estado] : '') : 'Anulada',
          // El manual: los estados por peso, no por colores nuevos. Lo que pide trabajo pesa mas.
          peso: !orden.estadoActivo
            ? 'font-normal'
            : estado === 'EN_EJECUCION'
              ? 'font-extrabold'
              : estado === 'CREADA'
                ? 'font-bold'
                : 'font-normal',
        };
      });
  });

  protected readonly hayError = computed(() => this.ordenes.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.ordenes.error()));

  private opciones(
    ids: readonly (string | undefined)[],
    nombres: ReadonlyMap<string, string>,
  ): { id: string; nombre: string }[] {
    return [...new Set(ids.filter((id): id is string => !!id))]
      .map((id) => ({ id, nombre: nombres.get(id) ?? 'No disponible' }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  }
}
