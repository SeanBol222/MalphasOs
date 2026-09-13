import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Destino de quien entra pero no alcanza la autoridad que una pantalla exige.
 *
 * <p>Vive dentro del armazon a proposito: quien llega aqui tiene sesion, y
 * dejarlo sin navegacion lo dejaria encerrado.
 *
 * <p>El texto sigue el tono del manual de marca: dice que pasa y que hacer, sin
 * disculpas ni exclamaciones.
 */
@Component({
  selector: 'app-sin-permiso',
  templateUrl: './sin-permiso.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SinPermiso {}
