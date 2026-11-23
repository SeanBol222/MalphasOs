import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { normalizar } from '../../../core/texto';
import { CabeceraDePieza } from './cabecera-de-pieza';
import { OrdenMovil } from './orden-movil';
import { ColumnaOrdenable, Sentido, compararEn, invertido } from './columna-ordenable';

/**
 * Las marcas del catalogo: el primer eslabon de la cadena.
 *
 * <p>Una marca es un nombre y nada mas, de modo que se maneja en linea: un formulario de un solo campo
 * no merece una pagina propia, y verlas junto a lo que se construye con ellas es como se trabaja.
 *
 * <p>Desde el 2026-11-20 es una tabla, como las demas piezas del catalogo, con la lupa y el alta en la
 * cabecera: el alta se abre con «Nueva marca» encima de la tabla.
 */
@Component({
  selector: 'app-marcas',
  imports: [ReactiveFormsModule, CabeceraDePieza, ColumnaOrdenable, OrdenMovil],
  templateUrl: './marcas.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Marcas {
  private readonly api = inject(CatalogoApi);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly marcas = this.api.listarMarcas();
  protected readonly alta = this.api.crearMarca();
  protected readonly cambio = this.api.renombrarMarca();
  protected readonly baja = this.api.retirarMarca();

  /**
   * La marca cuyo retiro se esta confirmando (2026-11-20, pedido por el usuario tras retirar una por
   * accidente): «Retirar» pregunta antes, y solo «Sí, retirar» lo hace. Ninguna pantalla la devuelve.
   */
  protected readonly confirmandoRetiro = signal<string | null>(null);

  private readonly sesion = inject(Sesion);

  /** Señal y no booleano: las autoridades se releen en cada evento de Keycloak. */
  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  protected readonly nombreNuevo = this.formBuilder.nonNullable.control('', [
    Validators.required,
    Validators.maxLength(50),
  ]);
  protected readonly nombreEditado = this.formBuilder.nonNullable.control('', [
    Validators.required,
    Validators.maxLength(50),
  ]);

  protected readonly renombrando = signal<string | null>(null);

  /** El alta se abre con «Nueva marca» y se cierra al anadir o al cancelar. */
  protected readonly abierto = signal(false);

  /** Lo escrito en la lupa de la cabecera. */
  protected readonly busqueda = signal('');

  /** El sentido del orden por nombre: de la A a la Z por defecto, y la cabecera lo invierte. */
  protected readonly sentido = signal<Sentido>('asc');

  /** Las marcas que casan con lo buscado, sin tildes ni mayusculas, en el orden elegido. */
  protected readonly visibles = computed(() => {
    const palabras = normalizar(this.busqueda()).split(' ').filter(Boolean);

    return (this.marcas.data() ?? [])
      .filter((marca) => {
        const texto = normalizar(marca.nombre ?? '');

        return palabras.every((palabra) => texto.includes(palabra));
      })
      .sort((a, b) => compararEn(this.sentido(), a.nombre, b.nombre));
  });

  protected invertirOrden(): void {
    this.sentido.update(invertido);
  }

  protected abrir(): void {
    this.nombreNuevo.reset();
    this.abierto.set(true);
  }

  protected cancelar(): void {
    this.nombreNuevo.reset();
    this.abierto.set(false);
  }

  protected readonly hayError = computed(
    () =>
      this.marcas.isError() || this.alta.isError() || this.cambio.isError() || this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.marcas.error() ?? this.alta.error() ?? this.cambio.error() ?? this.baja.error(),
    ),
  );

  protected agregar(): void {
    if (this.nombreNuevo.invalid) {
      this.nombreNuevo.markAsTouched();

      return;
    }

    this.alta.mutate({ nombre: this.nombreNuevo.value }, { onSuccess: () => this.cancelar() });
  }

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
      { id, cambio: { nombre: this.nombreEditado.value } },
      { onSuccess: () => this.renombrando.set(null) },
    );
  }

  protected retirar(id: string): void {
    this.baja.mutate(id, { onSettled: () => this.confirmandoRetiro.set(null) });
  }
}
