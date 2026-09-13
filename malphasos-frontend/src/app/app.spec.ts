import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('arranca', () => {
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });

  it('no pinta nada por si misma: solo aloja la ruta activa', async () => {
    // El armazon vive en el componente de disposicion. Si algun dia aparece
    // aqui una barra o un titulo, las rutas publicas lo arrastrarian.
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const raiz = fixture.nativeElement as HTMLElement;
    expect(raiz.querySelector('router-outlet')).toBeTruthy();
    expect(raiz.textContent?.trim()).toBe('');
  });
});
