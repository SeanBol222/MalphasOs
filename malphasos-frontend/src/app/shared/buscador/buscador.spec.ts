import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { instalarAlmacenamiento } from '../../../testing/almacenamiento';
import { anotarEnHistorial } from './historial';
import { Buscador, Opcion } from './buscador';

const PAISES: Opcion[] = [
  { id: 'co', etiqueta: 'Colombia' },
  { id: 'pe', etiqueta: 'Perú' },
  { id: 'mx', etiqueta: 'México' },
  { id: 'es', etiqueta: 'España' },
];

@Component({
  selector: 'app-anfitrion-opcional',
  imports: [ReactiveFormsModule, Buscador],
  template: `
    <app-buscador
      [formControl]="control"
      campo="paisOpcional"
      etiqueta="País"
      historial="pais"
      [opciones]="PAISES"
      [opcional]="true"
    />
  `,
})
class AnfitrionOpcional {
  readonly control = new FormControl('', { nonNullable: true });
  protected readonly PAISES = PAISES;
}

@Component({
  selector: 'app-anfitrion',
  imports: [ReactiveFormsModule, Buscador],
  template: `
    <app-buscador
      [formControl]="control"
      campo="pais"
      etiqueta="País"
      historial="pais"
      [opciones]="opciones()"
    />
  `,
})
class Anfitrion {
  readonly control = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly opciones = signal<readonly Opcion[]>(PAISES);
}

describe('Campo de búsqueda', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let desinstalar: () => void;

  beforeEach(() => {
    desinstalar = instalarAlmacenamiento();
    TestBed.configureTestingModule({});
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const entrada = () => raiz().querySelector<HTMLInputElement>('#pais')!;
  const sugerencias = () =>
    [...raiz().querySelectorAll('#pais-lista [role="option"]')].map((o) => o.textContent?.trim());

  function enfocar(): void {
    entrada().dispatchEvent(new Event('focus'));
    fixture.detectChanges();
  }

  function escribir(valor: string): void {
    enfocar();
    entrada().value = valor;
    entrada().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function teclear(key: string): void {
    entrada().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    fixture.detectChanges();
  }

  describe('Qué ofrece', () => {
    it('sin escribir nada no ofrece el catálogo entero', () => {
      // Es el motivo de todo el componente: abrir un desplegable de 249 paises no ayuda a nadie.
      enfocar();

      expect(sugerencias()).toEqual([]);
      expect(texto()).toContain('Escriba para buscar');
    });

    it('al escribir, busca en todo el catálogo', () => {
      escribir('es');

      // España por el principio y México por dentro: se busca por trozo, no por prefijo.
      expect(sugerencias()).toEqual(['España']);
    });

    it('busca sin tildes y sin mayúsculas', () => {
      // Quien teclea en un telefono no pone tildes, y no encontrar Perú por eso es la queja mas
      // segura de este tipo de campo.
      escribir('PERU');

      expect(sugerencias()).toEqual(['Perú']);
    });

    it('sin escribir nada, ofrece lo ya elegido antes y en orden de uso', () => {
      anotarEnHistorial('pais', 'pe');
      anotarEnHistorial('pais', 'mx');
      fixture = TestBed.createComponent(Anfitrion);
      fixture.detectChanges();
      enfocar();

      expect(sugerencias()).toEqual(['México', 'Perú']);
    });

    it('lo que ya no está en el catálogo no vuelve por el historial', () => {
      // Una opcion retirada sigue en el almacenamiento del navegador, y ofrecerla daria un error al
      // guardar que nadie sabria explicar.
      anotarEnHistorial('pais', 'xx');
      fixture = TestBed.createComponent(Anfitrion);
      fixture.detectChanges();
      enfocar();

      expect(sugerencias()).toEqual([]);
    });
  });

  describe('Qué le pasa al formulario', () => {
    it('elegir una sugerencia guarda el identificador, no el nombre', () => {
      escribir('col');
      raiz()
        .querySelector('#pais-lista [role="option"]')!
        .dispatchEvent(new MouseEvent('mousedown'));
      fixture.detectChanges();

      expect(fixture.componentInstance.control.value).toBe('co');
      expect(entrada().value).toBe('Colombia');
    });

    it('un nombre a medias deja el formulario sin valor', () => {
      // Sin esto, «Colom» pasaria por bueno y el backend responderia que el pais no existe.
      escribir('Colom');

      expect(fixture.componentInstance.control.value).toBe('');
      expect(fixture.componentInstance.control.invalid).toBe(true);
    });

    it('un nombre exacto vale aunque no se pulse la sugerencia', () => {
      // Quien teclea el nombre completo y sigue al campo siguiente espera que cuente.
      escribir('méxico');

      expect(fixture.componentInstance.control.value).toBe('mx');
    });

    it('un nombre que no existe se dice, y no se guarda nada', () => {
      escribir('Wakanda');

      expect(sugerencias()).toEqual([]);
      expect(texto()).toContain('No hay ninguna opción con ese nombre');
      expect(fixture.componentInstance.control.value).toBe('');
    });

    it('un valor que llega desde el formulario se muestra por su nombre', () => {
      // Es el caso de una pantalla de edicion: el identificador llega antes que el catalogo.
      fixture.componentInstance.control.setValue('es');
      fixture.detectChanges();

      expect(entrada().value).toBe('España');
    });

    it('y si el catálogo llega después, el nombre aparece igual', () => {
      const tardio = TestBed.createComponent(Anfitrion);
      tardio.componentInstance.opciones.set([]);
      tardio.componentInstance.control.setValue('co');
      tardio.detectChanges();

      expect(tardio.nativeElement.querySelector('#pais').value).toBe('');

      tardio.componentInstance.opciones.set(PAISES);
      tardio.detectChanges();

      expect(tardio.nativeElement.querySelector('#pais').value).toBe('Colombia');
    });
  });

  describe('Se maneja con el teclado, no solo con el ratón', () => {
    it('las flechas recorren las sugerencias y Enter elige', () => {
      escribir('a');
      teclear('ArrowDown');
      teclear('ArrowDown');
      const segunda = sugerencias()[1];
      teclear('Enter');

      expect(entrada().value).toBe(segunda);
      expect(fixture.componentInstance.control.value).not.toBe('');
    });

    it('Escape cierra la lista sin elegir', () => {
      escribir('co');

      expect(sugerencias().length).toBeGreaterThan(0);

      teclear('Escape');

      expect(sugerencias()).toEqual([]);
      expect(fixture.componentInstance.control.value).toBe('');
    });
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('es un combobox y dice si está desplegado', () => {
      expect(entrada().getAttribute('role')).toBe('combobox');
      expect(entrada().getAttribute('aria-expanded')).toBe('false');

      escribir('co');

      expect(entrada().getAttribute('aria-expanded')).toBe('true');
      expect(entrada().getAttribute('aria-controls')).toBe('pais-lista');
    });

    it('la opción resaltada se anuncia con aria-activedescendant', () => {
      // Sin esto, quien no ve la pantalla no se entera de por dónde va la selección.
      escribir('a');
      teclear('ArrowDown');

      expect(entrada().getAttribute('aria-activedescendant')).toBe('pais-opcion-0');
      expect(raiz().querySelector('#pais-opcion-0')!.getAttribute('aria-selected')).toBe('true');
    });

    it('la etiqueta está asociada al campo y el error, vinculado', () => {
      expect(raiz().querySelector('label[for="pais"]')).toBeTruthy();

      escribir('Wakanda');

      expect(entrada().getAttribute('aria-invalid')).toBe('true');
      expect(raiz().querySelector(`#${entrada().getAttribute('aria-describedby')}`)).toBeTruthy();
    });

    it('un campo obligatorio lleva asterisco y lo dice al lector de pantalla', () => {
      // El asterisco es la convencion visual; aria-required es lo que un lector anuncia. Solo lo
      // primero dejaria la informacion en un simbolo que no se puede oir.
      expect(raiz().querySelector('label[for="pais"]')!.textContent).toContain('*');
      expect(entrada().getAttribute('aria-required')).toBe('true');
    });

    it('el campo respeta el área táctil mínima', () => {
      expect(entrada().className).toContain('min-h-tactil');
    });
  });

  describe('Un campo opcional', () => {
    it('no lleva asterisco ni dice "opcional": lo que se marca es lo obligatorio', async () => {
      // Se invirtio la convencion el 2026-09-26: antes cada campo opcional lo decia, y en un
      // formulario con tres opcionales eso son tres lineas de ruido.
      const opcional = TestBed.createComponent(AnfitrionOpcional);
      opcional.detectChanges();

      const raizOpcional = opcional.nativeElement as HTMLElement;

      expect(raizOpcional.querySelector('label[for="paisOpcional"]')!.textContent).not.toContain('*');
      expect(raizOpcional.textContent).not.toContain('Opcional');
      expect(raizOpcional.querySelector('#paisOpcional')!.getAttribute('aria-required')).toBeNull();
    });
  });

  afterEach(() => {
    localStorage.clear();
    desinstalar();
  });
});
