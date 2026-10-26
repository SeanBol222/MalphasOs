import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClienteApi } from '../cliente-api';
import { SedeApi } from '../sede-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { UbicacionApi } from '../../location/ubicacion-api';
import { Sesion } from '../../../core/auth/sesion';
import { ETIQUETA_DE_IDENTIFICACION } from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta } from '../../../core/fechas';
import { normalizar } from '../../../core/texto';
import { conteoDe, contar } from '../conteos';

/**
 * Los clientes registrados, con lo que tienen: sedes, ciudades, equipos y el último servicio. Cierra la
 * mitad de lectura de RF-08.
 *
 * <p><b>Rehecha el 2026-10-11 con el rediseño</b> —la maqueta «A-Clientes»—. Los conteos salen de dos
 * resúmenes del servidor, uno por módulo —las sedes con sus áreas, de {@code client}, y los equipos por
 * área, de {@code equipment}—, y se suman aquí. El buscador filtra en el navegador: los clientes son
 * decenas, no cientos, y a diferencia de los equipos se piden enteros de todas formas.
 *
 * <p>Las columnas de equipos y de ciudades dependen de autoridades que quien lee clientes no siempre
 * tiene; sin ellas no se piden y la columna no sale.
 */
@Component({
  selector: 'app-lista-clientes',
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './lista-clientes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaClientes {
  private readonly sesion = inject(Sesion);

  protected readonly clientes = inject(ClienteApi).listar();
  private readonly sedes = inject(SedeApi).resumen();

  /**
   * Quien da de alta clientes, que no es quien solo los consulta: el representante de un cliente ve
   * el suyo y no tiene un «Nuevo cliente» que el servidor le rechazaría (2026-10-26).
   */
  protected readonly creaClientes = computed(() => this.sesion.puede('client.write'));
  protected readonly veEquipos = computed(() => this.sesion.puede('equipment.read'));
  protected readonly veCiudades = computed(() => this.sesion.puede('location.read'));
  private readonly equipos = inject(EquipoApi).conteoPorArea(undefined, this.veEquipos);
  private readonly ciudades = inject(UbicacionApi).listarCiudades(this.veCiudades);

  protected readonly buscador = new FormControl('', { nonNullable: true });
  private readonly texto = toSignal(this.buscador.valueChanges, { initialValue: '' });
  protected readonly retirados = signal(false);

  private readonly conteos = computed(() =>
    contar(this.sedes.data() ?? [], this.equipos.data() ?? [], 'idCliente'),
  );

  protected readonly filas = computed(() => {
    const buscado = normalizar(this.texto());
    const ciudades = new Map((this.ciudades.data() ?? []).map((c) => [c.id, c.nombre]));

    return (this.clientes.data() ?? [])
      .filter((cliente) => cliente.estadoActivo === !this.retirados())
      .filter(
        (cliente) =>
          !buscado ||
          [cliente.razonSocial, cliente.sigla, cliente.documento].some((campo) =>
            normalizar(campo ?? '').includes(buscado),
          ),
      )
      .sort((a, b) => (a.razonSocial ?? '').localeCompare(b.razonSocial ?? '', 'es'))
      .map((cliente) => {
        const conteo = conteoDe(this.conteos(), cliente.id);

        return {
          id: cliente.id!,
          sigla: cliente.sigla ?? '',
          razonSocial: cliente.razonSocial ?? '',
          documento: cliente.documento ?? '',
          tipoDocumento: cliente.tipoIdentificacion
            ? ETIQUETA_DE_IDENTIFICACION[cliente.tipoIdentificacion]
            : '',
          ciudades:
            conteo.idsCiudades
              .map((id) => ciudades.get(id))
              .filter(Boolean)
              .join(' · ') || '—',
          sedes: conteo.sedes,
          equipos: conteo.equipos,
          ultimoServicio: fechaCorta(conteo.ultimoServicio),
        };
      });
  });

  /** «14 clientes activos · 31 sedes · 312 equipos instalados», de lo que la lista ensena. */
  protected readonly resumen = computed(() => {
    const activos = (this.clientes.data() ?? []).filter((c) => c.estadoActivo);
    const conteos = activos.map((c) => conteoDe(this.conteos(), c.id));
    const sedes = conteos.reduce((suma, c) => suma + c.sedes, 0);
    const equipos = conteos.reduce((suma, c) => suma + c.equipos, 0);
    const partes = [
      `${activos.length} ${activos.length === 1 ? 'cliente activo' : 'clientes activos'}`,
      `${sedes} ${sedes === 1 ? 'sede' : 'sedes'}`,
    ];
    if (this.veEquipos()) {
      partes.push(`${equipos} ${equipos === 1 ? 'equipo instalado' : 'equipos instalados'}`);
    }

    return partes.join(' · ');
  });

  protected readonly mensajeDeError = computed(() => traducirError(this.clientes.error()));

  protected verRetirados(si: boolean): void {
    this.retirados.set(si);
  }
}
