import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Primera pantalla dentro de la sesión. Crecerá cuando haya módulos que listar. */
@Component({
  selector: 'app-inicio',
  templateUrl: './inicio.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Inicio {}
