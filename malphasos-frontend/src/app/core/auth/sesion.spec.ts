import { TestBed } from '@angular/core/testing';
import { proveerSesionFalsa } from '../../../testing/keycloak-falso';
import { Sesion } from './sesion';

function sesionCon(autoridades: readonly string[], autenticado = true): Sesion {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: proveerSesionFalsa({ autenticado, autoridades }) });

  return TestBed.inject(Sesion);
}

describe('Sesión', () => {
  it('reconoce la autoridad que el token trae', () => {
    expect(sesionCon(['work-order.read']).puede('work-order.read')).toBe(true);
  });

  it('niega la que no trae', () => {
    expect(sesionCon(['work-order.read']).puede('work-order.write')).toBe(false);
  });

  it('quien trae admin.full puede cualquier cosa, sin enumerar las 19', () => {
    // Es la unica regla del modelo de permisos que el frontend conoce. Copiar
    // aqui la lista de autoridades habria sido una segunda lista, y dos listas
    // escritas por separado se desincronizan.
    const sesion = sesionCon(['admin.full']);

    for (const autoridad of ['work-order.write', 'client.delete', 'equipment.assign']) {
      expect(sesion.puede(autoridad)).toBe(true);
    }
  });

  it('quien no ha entrado no puede nada', () => {
    expect(sesionCon([], false).puede('work-order.read')).toBe(false);
  });

  it('expone el nombre del token', () => {
    expect(sesionCon([]).nombre()).toBe('Sean Bolívar');
  });

  it('informa, no autoriza: el permiso lo comprueba el servidor', () => {
    // Esta prueba no ejerce codigo, fija una intencion. Sesion no expone nada
    // que impida una llamada: solo contesta si conviene enseñar un control.
    const metodos = Object.getOwnPropertyNames(Sesion.prototype);

    expect(metodos).toEqual(expect.arrayContaining(['puede', 'entrar', 'salir']));
    expect(metodos.some((m) => /bloque|deneg|autoriz/i.test(m))).toBe(false);
  });
});
