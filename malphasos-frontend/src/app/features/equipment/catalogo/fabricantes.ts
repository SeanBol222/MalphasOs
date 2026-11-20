import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoApi } from '../catalogo-api';
import { UbicacionApi } from '../../location/ubicacion-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { normalizar } from '../../../core/texto';
import { CabeceraDePieza } from './cabecera-de-pieza';
import { OrdenMovil } from './orden-movil';
import { ColumnaOrdenable, Sentido, compararEn, invertido } from './columna-ordenable';
import { Buscador, opcionesDe } from '../../../shared/buscador/buscador';

/**
 * Los fabricantes del catalogo, con su pais de origen.
 *
 * <p>El pais se elige de una lista: el contrato lo pide como identificador y un campo de texto
 * obligaria a teclear un UUID. Es opcional, igual que en el cliente, porque de algunos fabricantes
 * solo se conoce el nombre.
 *
 * <p><b>Fabricante y marca no son lo mismo</b>, y es la confusion mas probable de esta pantalla: la
 * marca es la linea comercial del equipo y el fabricante es quien lo produce, que puede ser otra
 * empresa y en otro pais. El esquema los separa y el modelo los junta.
 */
@Component({
  selector: 'app-fabricantes',
  imports: [ReactiveFormsModule, Buscador, CabeceraDePieza, ColumnaOrdenable, OrdenMovil],
  templateUrl: './fabricantes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Fabricantes {
  private readonly api = inject(CatalogoApi);
  private readonly sesion = inject(Sesion);

  protected readonly fabricantes = this.api.listarFabricantes();
  private readonly paises = inject(UbicacionApi).listarPaises();

  protected readonly opcionesDePais = computed(() => opcionesDe(this.paises.data()));
  protected readonly alta = this.api.crearFabricante();
  protected readonly baja = this.api.retirarFabricante();
  protected readonly cambio = this.api.editarFabricante();

  /**
   * El fabricante que se esta renombrando, en su propia fila, como las marcas (2026-11-20, pedido por el
   * usuario). Solo viaja el nombre: el pais se queda como estaba.
   */
  protected readonly renombrando = signal<string | null>(null);
  protected readonly nombreEditado = inject(FormBuilder).nonNullable.control('', [
    Validators.required,
    Validators.maxLength(50),
  ]);

  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    idPais: [''],
  });

  /** El alta se abre con «Nuevo fabricante» y se cierra al anadir o al cancelar. */
  protected readonly abierto = signal(false);

  /** Lo escrito en la lupa de la cabecera. */
  protected readonly busqueda = signal('');

  /** El sentido del orden por nombre: de la A a la Z por defecto, y la cabecera lo invierte. */
  protected readonly sentido = signal<Sentido>('asc');

  /** Los fabricantes que casan con lo buscado —en el nombre o en el pais—, en el orden elegido. */
  protected readonly visibles = computed(() => {
    const palabras = normalizar(this.busqueda()).split(' ').filter(Boolean);

    return (this.fabricantes.data() ?? [])
      .filter((fabricante) => {
        const texto = normalizar(`${fabricante.nombre ?? ''} ${this.pais(fabricante.idPais)}`);

        return palabras.every((palabra) => texto.includes(palabra));
      })
      .sort((a, b) => compararEn(this.sentido(), a.nombre, b.nombre));
  });

  protected invertirOrden(): void {
    this.sentido.update(invertido);
  }

  protected abrir(): void {
    this.formulario.reset();
    this.abierto.set(true);
  }

  protected cancelar(): void {
    this.formulario.reset();
    this.abierto.set(false);
  }

  protected readonly hayError = computed(
    () =>
      this.fabricantes.isError() ||
      this.alta.isError() ||
      this.cambio.isError() ||
      this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.fabricantes.error() ?? this.alta.error() ?? this.cambio.error() ?? this.baja.error(),
    ),
  );

  protected empezarARenombrar(id: string, nombre: string | undefined): void {
    this.nombreEditado.setValue(nombre ?? '');
    this.renombrando.set(id);
  }

  protected renombrar(id: string): void {
    if (this.nombreEditado.invalid) {
      this.nombreEditado.markAsTouched();

      return;
    }

    this.cambio.mutate(
      { id, cambio: { nombre: this.nombreEditado.value.trim() } },
      { onSuccess: () => this.renombrando.set(null) },
    );
  }

  /** El pais de cada fabricante, en palabras. Sin catalogo no se inventa: se dice que no esta. */
  protected pais(idPais: string | undefined): string {
    if (!idPais) {
      return 'Sin especificar';
    }

    return this.paises.data()?.find((p) => p.id === idPais)?.nombre ?? 'No disponible';
  }

  protected agregar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const { nombre, idPais } = this.formulario.getRawValue();

    this.alta.mutate(idPais ? { nombre, idPais } : { nombre }, {
      onSuccess: () => this.cancelar(),
    });
  }

  protected retirar(id: string): void {
    this.baja.mutate(id);
  }
}
