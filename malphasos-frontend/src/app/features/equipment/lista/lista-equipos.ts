import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { ClienteApi } from '../../client/cliente-api';
import { CatalogoApi } from '../catalogo-api';
import { EquipoApi } from '../equipo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta } from '../../../core/fechas';
import {
  ConsultaDeEquipos,
  ETIQUETA_DE_RESULTADO,
  FilaDeResumenDeEquipos,
  ResultadoDeServicio,
} from '../../../core/api/tipos';

/** Cuantas filas trae cada pagina. */
export const TAMANO_DE_PAGINA = 25;

/** Lo que espera el buscador antes de preguntar: una peticion por palabra, no por tecla. */
export const ESPERA_DEL_BUSCADOR_MS = 300;

/**
 * Todos los equipos instalados, con donde estan y como quedaron en su ultimo servicio.
 *
 * <p><b>Rehecho el 2026-10-11 con el rediseño</b> —la maqueta «A-Equipos»—. Hasta entonces la pantalla
 * pedia la lista entera de unidades y la cruzaba con cuatro listas del catalogo y una peticion por
 * area, y aun asi no podia decir cuando fue el ultimo servicio ni como quedo el equipo. Ahora lo
 * compila el servidor —{@code GET /client-equipments/overview}—, que ademas filtra y pagina: con
 * trescientos equipos, filtrar en el navegador era bajarlos todos.
 *
 * <p>El estado de cada equipo se distingue <b>por peso tipografico y un cuadro de tinta</b>, no por
 * colores nuevos: es la regla del manual de marca.
 */
@Component({
  selector: 'app-lista-equipos',
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './lista-equipos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaEquipos {
  private readonly sesion = inject(Sesion);

  protected readonly buscador = new FormControl('', { nonNullable: true });
  protected readonly cliente = new FormControl('', { nonNullable: true });
  protected readonly tipo = new FormControl('', { nonNullable: true });

  private readonly texto = toSignal(
    this.buscador.valueChanges.pipe(
      debounceTime(ESPERA_DEL_BUSCADOR_MS),
      map((valor) => valor.trim()),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );
  private readonly idCliente = toSignal(this.cliente.valueChanges, { initialValue: '' });
  private readonly idTipo = toSignal(this.tipo.valueChanges, { initialValue: '' });

  protected readonly soloFueraDeServicio = signal(false);
  protected readonly dadosDeBaja = signal(false);
  protected readonly pagina = signal(0);

  private readonly consulta = computed<ConsultaDeEquipos>(() => ({
    q: this.texto() || undefined,
    idCliente: this.idCliente() || undefined,
    idTipoEquipo: this.idTipo() || undefined,
    resultado: this.soloFueraDeServicio() ? 'FUERA_DE_SERVICIO' : undefined,
    dadosDeBaja: this.dadosDeBaja() || undefined,
    pagina: this.pagina(),
    tamano: TAMANO_DE_PAGINA,
  }));

  protected readonly resumen = inject(EquipoApi).resumen(this.consulta);

  /**
   * El filtro por cliente solo se ofrece a quien puede leer clientes: un supervisor ve los suyos y
   * el filtro le sobraria, y pedir la lista sin permiso seria un 403 para nada.
   */
  protected readonly puedeFiltrarPorCliente = computed(() => this.sesion.puede('client.read'));
  protected readonly clientes = inject(ClienteApi).listar(this.puedeFiltrarPorCliente);
  protected readonly tipos = inject(CatalogoApi).listarTipos();

  protected readonly puedeRegistrar = computed(() => this.sesion.puede('equipment.assign'));

  protected readonly filas = computed(() => (this.resumen.data()?.filas ?? []).map(aFila));
  protected readonly total = computed(() => this.resumen.data()?.total ?? 0);

  /** «312 equipos instalados», «1 equipo dado de baja»: el participio concuerda con el número. */
  protected readonly recuento = computed(() => {
    const total = this.total();
    const uno = total === 1;
    const estado = this.dadosDeBaja()
      ? uno
        ? 'dado de baja'
        : 'dados de baja'
      : uno
        ? 'instalado'
        : 'instalados';

    return `${total} ${uno ? 'equipo' : 'equipos'} ${estado}${this.filtrando() ? ' con estos filtros' : ''}`;
  });

  /** «1–25 de 312». */
  protected readonly rango = computed(() => {
    const total = this.total();
    if (!total) {
      return '0 equipos';
    }
    const desde = this.pagina() * TAMANO_DE_PAGINA + 1;
    const hasta = Math.min(desde + TAMANO_DE_PAGINA - 1, total);

    return `${desde}–${hasta} de ${total}`;
  });

  protected readonly hayAnterior = computed(() => this.pagina() > 0);
  protected readonly haySiguiente = computed(
    () => (this.pagina() + 1) * TAMANO_DE_PAGINA < this.total(),
  );

  protected readonly filtrando = computed(
    () =>
      !!(this.texto() || this.idCliente() || this.idTipo()) ||
      this.soloFueraDeServicio() ||
      this.dadosDeBaja(),
  );

  protected readonly hayError = computed(() => this.resumen.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.resumen.error()));

  constructor() {
    // Cambiar un filtro vuelve a la primera pagina: la tercera de otra busqueda no significa nada.
    this.buscador.valueChanges.subscribe(() => this.pagina.set(0));
    this.cliente.valueChanges.subscribe(() => this.pagina.set(0));
    this.tipo.valueChanges.subscribe(() => this.pagina.set(0));
  }

  protected alternarFueraDeServicio(): void {
    this.soloFueraDeServicio.update((valor) => !valor);
    this.pagina.set(0);
  }

  protected alternarDadosDeBaja(): void {
    this.dadosDeBaja.update((valor) => !valor);
    this.pagina.set(0);
  }

  protected anterior(): void {
    this.pagina.update((pagina) => Math.max(pagina - 1, 0));
  }

  protected siguiente(): void {
    this.pagina.update((pagina) => pagina + 1);
  }

  protected limpiar(): void {
    this.buscador.setValue('');
    this.cliente.setValue('');
    this.tipo.setValue('');
    this.soloFueraDeServicio.set(false);
    this.dadosDeBaja.set(false);
  }
}

/** Como se pinta el resultado: peso tipografico y un cuadro, del mas leve al mas grave. */
const ASPECTO_DEL_RESULTADO: Readonly<
  Record<ResultadoDeServicio | 'NINGUNO', { peso: string; cuadro: string }>
> = {
  NINGUNO: { peso: 'font-normal', cuadro: 'bg-transparent' },
  OPERATIVO: { peso: 'font-medium', cuadro: 'bg-transparent' },
  OPERATIVO_CON_RESTRICCIONES: { peso: 'font-bold', cuadro: 'bg-neutral-500' },
  FUERA_DE_SERVICIO: { peso: 'font-extrabold', cuadro: 'bg-ink' },
};

function aFila(fila: FilaDeResumenDeEquipos) {
  const resultado = fila.resultado ?? 'NINGUNO';

  return {
    id: fila.id!,
    numeroHojaVida: fila.numeroHojaVida || '—',
    tipo: fila.tipoEquipo ?? '',
    modelo: [fila.marca, fila.modelo].filter(Boolean).join(' · '),
    serie: fila.serie ?? '',
    cliente: fila.cliente ?? 'Cliente no disponible',
    lugar: [fila.sede, fila.areaServicio].filter(Boolean).join(' · '),
    idArea: fila.idAreaServicio,
    ultimoServicio: fechaCorta(fila.ultimoServicio),
    resultado: fila.resultado ? ETIQUETA_DE_RESULTADO[fila.resultado] : 'Sin servicios',
    ...ASPECTO_DEL_RESULTADO[resultado],
    deBaja: !fila.estadoActivo,
  };
}
