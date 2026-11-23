import { TipoDeEquipo } from '../../../core/api/tipos';

/**
 * Si un tipo de equipo de la orden pide patrones (2026-11-22, pedido por el usuario): solo los que el
 * catálogo declara con alguna verificación metrológica. Un agitador vórtex o un analizador que no se
 * verifican no tienen contra qué medirse, y ofrecerles patrones solo alarga la lista.
 *
 * <p>Uno sin verificación que aun así lleve patrones elegidos —se le quitaron después— sigue saliendo,
 * para poder quitárselos. Y uno que el catálogo no trae no se da por no verificable: no se sabe.
 */
export function pidePatrones(
  tipo: TipoDeEquipo | undefined,
  elegidos: readonly string[] | undefined,
): boolean {
  return !tipo || !!tipo.verificaciones?.length || !!elegidos?.length;
}
