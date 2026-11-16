import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { traducirError } from '../../core/errores/traducir';
import { InformeDeCarga, ProblemaDeCarga } from '../../core/api/tipos';
import { CargaApi } from './carga-api';

/** Cómo se llama en pantalla cada sección del resumen; la clave es la del formato. */
const SECCIONES: Readonly<Record<string, string>> = {
  marcas: 'Marcas',
  fabricantes: 'Fabricantes',
  protocolos: 'Protocolos',
  tiposEquipo: 'Tipos de equipo',
  modelos: 'Modelos',
  patrones: 'Patrones',
  personas: 'Personas',
  firmas: 'Firmas',
  clientes: 'Clientes',
  sedes: 'Sedes',
  areas: 'Áreas',
  equipos: 'Equipos',
  ordenes: 'Órdenes de trabajo',
  reportes: 'Reportes',
};

export function nombreDeSeccion(seccion: string | undefined): string {
  return (seccion && SECCIONES[seccion]) ?? seccion ?? '';
}

/** Los problemas de un archivo, o de la carga entera si el archivo es nulo. */
export interface ProblemasDeUnArchivo {
  readonly archivo: string | undefined;
  readonly problemas: readonly ProblemaDeCarga[];
}

/**
 * Los problemas agrupados por archivo, en el orden en que aparece cada uno (2026-11-16): una carga entera
 * son decenas de archivos, y una lista plana mezcla los de un cliente con los de otro.
 */
export function porArchivo(
  problemas: readonly ProblemaDeCarga[] | undefined,
): ProblemasDeUnArchivo[] {
  const grupos = new Map<string | undefined, ProblemaDeCarga[]>();
  for (const problema of problemas ?? []) {
    const archivo = problema.archivo || undefined;
    grupos.set(archivo, [...(grupos.get(archivo) ?? []), problema]);
  }
  return [...grupos].map(([archivo, deEse]) => ({ archivo, problemas: deEse }));
}

/**
 * Importar datos de otro sistema (2026-11-08), solo para el SuperUsuario: los archivos del formato
 * {@code malphasos-carga/1} que publica {@code contracts/carga/}. Desde el 2026-11-16, lo normal es un
 * solo ZIP con todo, el que escribe el conversor; los JSON sueltos siguen valiendo, y cuántos caben lo
 * dice el servidor.
 *
 * <p><b>Validar no escribe nada, y aplicar exige haber validado esos mismos archivos sin errores.</b>
 * Elegir otros borra el informe, de modo que hay que volver a validar: el botón de aplicar responde a lo
 * que se ve en el informe. El servidor vuelve a validar al aplicar, de modo que esto no protege nada: evita
 * aplicar a ciegas.
 *
 * <p>Aplicar es todo o nada y pide confirmación en la propia pantalla, como el retiro de un cliente.
 */
@Component({
  selector: 'app-importar-datos',
  templateUrl: './importar-datos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportarDatos {
  private readonly api = inject(CargaApi);

  protected readonly validacion = this.api.validar();
  protected readonly aplicacion = this.api.aplicar();

  protected readonly archivos = signal<readonly File[]>([]);
  protected readonly confirmando = signal(false);

  /** El último informe que hay que leer: el de aplicar si se aplicó, y si no el de validar. */
  protected readonly informe = computed<InformeDeCarga | undefined>(
    () => this.aplicacion.data() ?? this.validacion.data(),
  );
  protected readonly aplicada = computed(() => this.aplicacion.data()?.valida === true);
  protected readonly rechazada = computed(() => this.aplicacion.data()?.valida === false);

  protected readonly puedeValidar = computed(
    () =>
      this.archivos().length > 0 && !this.validacion.isPending() && !this.aplicacion.isPending(),
  );
  protected readonly puedeAplicar = computed(
    () =>
      this.validacion.data()?.valida === true &&
      !this.aplicacion.data() &&
      !this.aplicacion.isPending(),
  );

  protected readonly erroresPorArchivo = computed(() => porArchivo(this.informe()?.errores));
  protected readonly avisosPorArchivo = computed(() => porArchivo(this.informe()?.avisos));

  protected readonly hayError = computed(
    () => this.validacion.isError() || this.aplicacion.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.validacion.error() ?? this.aplicacion.error()),
  );

  protected readonly nombreDeSeccion = nombreDeSeccion;

  /**
   * Lo elegido se suma a lo que ya había (2026-11-08): el catálogo y el par de cada cliente viven en
   * carpetas distintas, y el diálogo del navegador elige de una sola. El mismo archivo dos veces no se
   * repite. El campo se vacía después, para que volver a elegir el que se acaba de quitar dispare otra vez.
   */
  protected elegir(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const nuevos = [...(campo.files ?? [])].filter(
      (archivo) => !this.archivos().some((ya) => mismoArchivo(ya, archivo)),
    );
    campo.value = '';
    if (nuevos.length) {
      this.cambiarArchivos([...this.archivos(), ...nuevos]);
    }
  }

  protected quitar(indice: number): void {
    this.cambiarArchivos(this.archivos().filter((_, i) => i !== indice));
  }

  /** Otros archivos son otra carga: el informe de antes ya no dice nada de ella. */
  private cambiarArchivos(archivos: readonly File[]): void {
    this.archivos.set(archivos);
    this.confirmando.set(false);
    this.validacion.reset();
    this.aplicacion.reset();
  }

  protected validar(): void {
    const archivos = this.archivos();
    this.confirmando.set(false);
    this.aplicacion.reset();
    this.validacion.mutate(archivos);
  }

  protected aplicar(): void {
    this.confirmando.set(false);
    this.aplicacion.mutate(this.archivos());
  }
}

/** El navegador no da la ruta: el mismo archivo es el mismo nombre, tamaño y fecha de modificación. */
function mismoArchivo(a: File, b: File): boolean {
  return a.name === b.name && a.size === b.size && a.lastModified === b.lastModified;
}
