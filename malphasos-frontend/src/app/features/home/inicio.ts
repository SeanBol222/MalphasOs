import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Sesion } from '../../core/auth/sesion';
import { HOY } from '../../core/fechas';
import { Agenda } from './agenda/agenda';

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];

/**
 * Primera pantalla dentro de la sesion: el saludo y, para quien lee ordenes, la agenda.
 *
 * <p>Hasta el 2026-10-10 era un titulo y una linea, con el comentario «crecera cuando haya modulos que
 * listar». Crecio con la agenda del rediseño. Quien no lee ordenes —el grupo de un cliente no tiene por
 * que— sigue viendo el saludo solo: la agenda no se oculta, no se pinta, porque pediria un periodo que
 * el servidor le negaria.
 */
@Component({
  selector: 'app-inicio',
  imports: [Agenda],
  templateUrl: './inicio.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Inicio {
  private readonly sesion = inject(Sesion);
  private readonly hoy = inject(HOY)();

  protected readonly fecha = `${DIAS[this.hoy.getDay()]} ${this.hoy.getDate()} de ${MESES[this.hoy.getMonth()]}`;

  protected readonly saludo = computed(() => {
    const nombre = String(this.sesion.nombre()).split(/\s+/)[0];
    const hora = this.hoy.getHours();
    const saludo = hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';

    return nombre ? `${saludo}, ${nombre}` : saludo;
  });

  protected readonly veOrdenes = computed(() => this.sesion.puede('work-order.read'));
}
