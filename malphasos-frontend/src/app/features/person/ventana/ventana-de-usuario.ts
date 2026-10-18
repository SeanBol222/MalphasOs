import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Sesion } from '../../../core/auth/sesion';
import { ETIQUETA_DE_TIPO_DE_PERSONA } from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta } from '../../../core/fechas';
import { LienzoDeFirma } from '../../../shared/lienzo-de-firma/lienzo-de-firma';
import { FirmaApi } from '../firma-api';
import { nombreCompleto, PersonaApi } from '../persona-api';

/** La autoridad que solo tiene el SuperUsuario: ningún grupo la concede. */
const SUPER_USUARIO = 'super.admin.full';

/**
 * La ventana de quien tiene la sesión, que se abre desde su nombre al pie de la barra lateral
 * (2026-10-17, diseño aprobado por el usuario sobre una propuesta en tres pantallas).
 *
 * <p>Reúne lo que es de esa persona y de nadie más: sus datos, <b>su firma digital</b> —que hasta
 * entonces era una página aparte, «Mi firma», enlazada con letra pequeña junto al nombre— y cerrar
 * sesión. Los datos son de solo lectura: editarlos sigue siendo cosa de «Personas».
 *
 * <p><b>Tres sesiones distintas caben aquí.</b> La de quien cierra informes ve y cambia su firma; la
 * de un representante de cliente ve sus datos sin sección de firma, porque no la necesita; y la del
 * SuperUsuario no tiene ficha de persona —se crea a mano en Keycloak— y lo dice, en lugar de mostrar
 * datos vacíos o un «todavía no tiene firma» que no podría arreglar.
 *
 * <p>Es un diálogo modal: atrapa el foco mientras está abierto y lo devuelve a quien lo abrió al
 * cerrarse, que es lo que WCAG pide y lo que {@code CdkTrapFocus} hace con su captura automática.
 */
@Component({
  selector: 'app-ventana-de-usuario',
  imports: [CdkTrapFocus, LienzoDeFirma],
  templateUrl: './ventana-de-usuario.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(keydown.escape)': 'cerrar.emit()' },
})
export class VentanaDeUsuario {
  /** Se pide cerrar: la abre y la cierra el armazón, que es quien sabe dónde devolver el foco. */
  readonly cerrar = output<void>();

  protected readonly sesion = inject(Sesion);
  private readonly firmas = inject(FirmaApi);

  protected readonly persona = inject(PersonaApi).yo();

  /** Si la sesión cierra informes, que es quien necesita firma. */
  protected readonly firmaInformes = computed(() => this.sesion.puede('report.write'));

  /** Sin ficha de persona no hay dónde guardar una firma, y su 404 se leería como «no tiene». */
  private readonly tieneFicha = computed(() => !!this.persona.data());

  protected readonly firma = this.firmas.consultar(
    computed(() => this.firmaInformes() && this.tieneFicha()),
  );
  protected readonly guardado = this.firmas.guardar();

  protected readonly nombre = computed((): string => {
    const persona = this.persona.data();

    return persona ? nombreCompleto(persona) : String(this.sesion.nombre());
  });

  protected readonly iniciales = computed(() =>
    this.nombre()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((palabra) => palabra[0]!.toUpperCase())
      .join(''),
  );

  protected readonly rol = computed(() => {
    const tipo = this.persona.data()?.tipoPersona;

    if (tipo) {
      return ETIQUETA_DE_TIPO_DE_PERSONA[tipo];
    }

    return this.sesion.autoridades().has(SUPER_USUARIO)
      ? ETIQUETA_DE_TIPO_DE_PERSONA.SUPER_ADMIN
      : '';
  });

  /**
   * El correo de la cuenta, o el primero vigente si la ficha aún no lo tiene marcado; y el primer
   * teléfono vigente. La ficha completa vive en «Personas».
   */
  protected readonly correo = computed(() => {
    const vigentes = (this.persona.data()?.emailPersonList ?? []).filter(
      (c) => c.estadoActivo !== false,
    );

    return (vigentes.find((c) => c.principal) ?? vigentes[0])?.correoPersona ?? '';
  });
  protected readonly telefono = computed(
    () =>
      this.persona.data()?.phonePersonList?.find((t) => t.estadoActivo !== false)
        ?.telefonoPersona ?? '',
  );

  protected readonly fechaCorta = (fecha: string | undefined) => fechaCorta(fecha?.slice(0, 10));

  /** Si se está dibujando una firma nueva en vez de mirar la guardada. */
  protected readonly cambiando = signal(false);
  protected readonly trazo = signal<string | null>(null);
  protected readonly faltaTrazo = signal(false);
  protected readonly guardadaConExito = signal(false);

  protected readonly hayError = computed(
    () => this.persona.isError() || this.firma.isError() || this.guardado.isError(),
  );
  protected readonly mensajeDeError = computed(() => {
    const fallo = this.persona.error() ?? this.firma.error() ?? this.guardado.error();

    return fallo instanceof HttpErrorResponse && fallo.error?.code === 'ERR_PERSON_001'
      ? 'Su cuenta no corresponde a ninguna persona registrada. Para guardar una firma, pida que le den de alta como ingeniero o administrador.'
      : traducirError(fallo);
  });

  protected empezarACambiar(): void {
    this.cambiando.set(true);
    this.trazo.set(null);
    this.faltaTrazo.set(false);
    this.guardadaConExito.set(false);
    this.guardado.reset();
  }

  protected cancelar(): void {
    this.cambiando.set(false);
    this.faltaTrazo.set(false);
    this.guardado.reset();
  }

  protected alDibujar(imagen: string | null): void {
    this.trazo.set(imagen);
    if (imagen) {
      this.faltaTrazo.set(false);
    }
  }

  /** No se acepta vacía, como pide RF-18: guardar sin trazo lo dice en vez de mandar nada. */
  protected guardar(): void {
    const imagen = this.trazo();

    if (!imagen) {
      this.faltaTrazo.set(true);

      return;
    }

    this.guardado.mutate(imagen, {
      onSuccess: () => {
        this.cambiando.set(false);
        this.guardadaConExito.set(true);
      },
    });
  }

  protected cambiarContrasena(): void {
    void this.sesion.cambiarContrasena();
  }

  protected salir(): void {
    void this.sesion.salir();
  }
}
