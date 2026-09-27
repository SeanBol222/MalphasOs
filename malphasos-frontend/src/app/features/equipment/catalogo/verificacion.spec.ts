import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { asentar } from '../../../../testing/pantalla';
import { ConfiguracionDeVerificacion, Verificacion } from './verificacion';

@Component({
  selector: 'app-anfitrion-verificacion',
  imports: [Verificacion],
  template: `<app-verificacion [inicial]="inicial()" (cambio)="ultima.set($event)" />`,
})
class Anfitrion {
  readonly inicial = signal<ConfiguracionDeVerificacion | null>(null);
  readonly ultima = signal<ConfiguracionDeVerificacion | null>(null);
}

describe('Cómo se verifica un tipo de equipo', () => {
  let fixture: ComponentFixture<Anfitrion>;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    fixture = TestBed.createComponent(Anfitrion);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const ultima = () => fixture.componentInstance.ultima();

  async function abrirCon(inicial: ConfiguracionDeVerificacion | null): Promise<void> {
    fixture.componentInstance.inicial.set(inicial);
    fixture.detectChanges();
    await asentar(fixture);
  }

  it('lo ya guardado se ve puesto al abrir', async () => {
    await abrirCon({
      modalidad: 'EQUIPO_CONSTANTE',
      cantidadDatos: 4,
      puntos: [{ valor: 250, unidad: 'mL/h' }],
      valida: true,
    });

    expect(raiz().querySelector<HTMLSelectElement>('#modalidadVerificacion')!.value).toBe(
      'EQUIPO_CONSTANTE',
    );
    expect(raiz().querySelector<HTMLInputElement>('#cantidadDatos')!.value).toBe('4');
    expect(raiz().querySelector<HTMLInputElement>('#punto-unidad-0')!.value).toBe('mL/h');
  });

  it('un dato incoherente que venga del servidor no se reenvía', async () => {
    // Es alcanzable de verdad: si una fila tuviera puntos con la modalidad variable, la pantalla los
    // pintaría y, sin este recorte, los mandaría de vuelta para que el backend los rechace. Los dos
    // campos solo existen con una modalidad constante.
    await abrirCon({
      modalidad: 'PATRON_EQUIPO_VARIABLE',
      cantidadDatos: 7,
      puntos: [{ valor: 100, unidad: 'mmHg' }],
      valida: true,
    });

    expect(ultima()?.modalidad).toBe('PATRON_EQUIPO_VARIABLE');
    expect(ultima()?.cantidadDatos).toBeNull();
    expect(ultima()?.puntos).toEqual([]);
    expect(ultima()?.valida).toBe(true);
  });

  it('una modalidad constante sin puntos no se da por válida', async () => {
    await abrirCon({
      modalidad: 'PATRON_CONSTANTE',
      cantidadDatos: 3,
      puntos: [],
      valida: true,
    });

    // El padre mira esto para no enviar: el backend lo rechazaria con un 400 que nadie sabria leer.
    expect(ultima()?.valida).toBe(false);
  });

  it('un tipo que no se verifica es válido sin nada más', async () => {
    await abrirCon(null);

    expect(ultima()?.modalidad).toBeNull();
    expect(ultima()?.valida).toBe(true);
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('cada campo lleva su etiqueta asociada, también los puntos', async () => {
      await abrirCon({
        modalidad: 'PATRON_CONSTANTE',
        cantidadDatos: 3,
        puntos: [{ valor: 100, unidad: 'mmHg' }],
        valida: true,
      });

      for (const id of ['modalidadVerificacion', 'cantidadDatos', 'punto-valor-0', 'punto-unidad-0']) {
        expect(raiz().querySelector(`label[for="${id}"]`)).toBeTruthy();
      }
    });

    it('el botón de quitar un punto dice cuál quita', async () => {
      // «Quitar» repetido cinco veces no le dice nada a quien no ve la pantalla.
      await abrirCon({
        modalidad: 'PATRON_CONSTANTE',
        cantidadDatos: 3,
        puntos: [{ valor: 100, unidad: 'mmHg' }],
        valida: true,
      });

      expect(raiz().querySelector('button[aria-label="Quitar el punto 1"]')).toBeTruthy();
    });

    it('los controles respetan el área táctil mínima', async () => {
      await abrirCon({
        modalidad: 'PATRON_CONSTANTE',
        cantidadDatos: 3,
        puntos: [{ valor: 100, unidad: 'mmHg' }],
        valida: true,
      });

      for (const control of raiz().querySelectorAll('input, select, button')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });
  });
});
