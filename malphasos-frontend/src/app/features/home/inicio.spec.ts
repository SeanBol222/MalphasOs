import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { proveerApiVacio } from '../../../testing/entorno';
import { proveerSesionFalsa } from '../../../testing/keycloak-falso';
import { HOY } from '../../core/fechas';
import { Inicio } from './inicio';

describe('Inicio', () => {
  function pintar(autoridades: readonly string[], hora = 9): HTMLElement {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiVacio(),
        ...proveerSesionFalsa({ autoridades, nombre: 'Laura Méndez' }),
        provideRouter([]),
        { provide: HOY, useValue: () => new Date(2026, 9, 6, hora, 0) },
      ],
    });
    const fixture = TestBed.createComponent(Inicio);
    fixture.detectChanges();

    return fixture.nativeElement as HTMLElement;
  }

  it('saluda por el nombre de pila y dice qué día es', () => {
    const raiz = pintar([]);

    expect(raiz.querySelector('h1')!.textContent!.trim()).toBe('Buenos días, Laura');
    expect(raiz.textContent).toContain('martes 6 de octubre');
  });

  it('el saludo cambia con la hora', () => {
    expect(pintar([], 15).querySelector('h1')!.textContent).toContain('Buenas tardes');
  });

  it('quien lee órdenes ve la agenda', () => {
    const raiz = pintar(['work-order.read']);

    expect(raiz.querySelector('app-agenda')).toBeTruthy();
  });

  it('quien no lee órdenes no la ve: pediría un periodo que el servidor le negaría', () => {
    const raiz = pintar(['client.read', 'equipment.read']);

    expect(raiz.querySelector('app-agenda')).toBeNull();
  });
});
