import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LienzoDeFirma } from './lienzo-de-firma';

@Component({
  imports: [LienzoDeFirma],
  template: `<app-lienzo-de-firma (cambio)="recibidas.push($event)" />`,
})
class Anfitrion {
  readonly recibidas: (string | null)[] = [];
}

/** El área de firma de RF-18 (2026-10-17). */
describe('Lienzo de firma', () => {
  let fixture: ComponentFixture<Anfitrion>;

  const original = HTMLCanvasElement.prototype.toDataURL;

  beforeEach(() => {
    // El entorno de pruebas no pinta: toDataURL no está implementado. Se dobla para probar el cableado;
    // el trazo de verdad se comprueba en un navegador.
    HTMLCanvasElement.prototype.toDataURL = () => 'data:image/png;base64,iVBORw0KGgo=';
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
  });

  afterEach(() => {
    HTMLCanvasElement.prototype.toDataURL = original;
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const lienzo = () => raiz().querySelector('canvas')!;
  const puntero = (tipo: string, x: number, y: number) =>
    lienzo().dispatchEvent(
      Object.assign(new MouseEvent(tipo, { clientX: x, clientY: y, bubbles: true }), {
        pointerId: 1,
      }),
    );

  function trazar(): void {
    puntero('pointerdown', 10, 10);
    puntero('pointermove', 40, 30);
    puntero('pointerup', 40, 30);
    fixture.detectChanges();
  }

  it('empieza vacío y lo dice', () => {
    expect(raiz().textContent).toContain('Sin firma todavía');
  });

  it('al terminar un trazo entrega la firma como PNG en data: URI', () => {
    trazar();

    expect(raiz().textContent).toContain('Firma dibujada');
    expect(fixture.componentInstance.recibidas).toHaveLength(1);
    expect(fixture.componentInstance.recibidas[0]).toMatch(/^data:image\/png/);
  });

  it('mover el puntero sin haberlo apoyado no dibuja nada', () => {
    puntero('pointermove', 40, 30);
    puntero('pointerup', 40, 30);
    fixture.detectChanges();

    expect(fixture.componentInstance.recibidas).toHaveLength(0);
    expect(raiz().textContent).toContain('Sin firma todavía');
  });

  it('«Limpiar» borra el intento y avisa de que ya no hay firma', () => {
    trazar();
    [...raiz().querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Limpiar')!
      .click();
    fixture.detectChanges();

    expect(fixture.componentInstance.recibidas.at(-1)).toBeNull();
    expect(raiz().textContent).toContain('Sin firma todavía');
  });
});
