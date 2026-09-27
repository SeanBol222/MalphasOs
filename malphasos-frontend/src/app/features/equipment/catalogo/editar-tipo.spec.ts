import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import { ID_TIPO, TIPOS, URL_TIPOS } from '../../../../testing/catalogo';
import { EditarTipo } from './editar-tipo';

const URL = `${URL_TIPOS}/${ID_TIPO}`;

@Component({ selector: 'app-catalogo-falso', template: '' })
class CatalogoFalso {}

describe('Edicion de un tipo de equipo', () => {
  let fixture: ComponentFixture<EditarTipo>;
  let http: HttpTestingController;

  let desinstalarAlmacenamiento: () => void;

  beforeEach(() => {
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'catalogo', component: CatalogoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(EditarTipo);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_TIPO);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, TIPOS[0]);
    // Y la lista de tipos, de la que salen las sugerencias de tecnología.
    await responderA(fixture, http, URL_TIPOS, TIPOS);
  }

  it('llega con la ficha tecnica ya puesta', async () => {
    await abrir();

    expect(raiz().querySelector<HTMLInputElement>('#nombre')!.value).toBe('Tensiómetro');
    expect(raiz().querySelector<HTMLTextAreaElement>('#definicionTecnica')!.value).toBe(
      'Mide presión arterial',
    );
  });

  it('no ofrece la modalidad de verificacion, porque el contrato no la admite aqui', async () => {
    // El backend la dejo fuera de EquipmentTypeUpdateRequest y le dio ruta propia. Un campo aqui
    // parecería funcionar y el cambio se perderia en silencio.
    await abrir();

    expect(raiz().querySelector('#modalidadVerificacion')).toBeNull();
  });

  it('manda un PATCH con los campos que el contrato admite', async () => {
    await abrir();
    const campo = raiz().querySelector<HTMLInputElement>('#nombre')!;
    campo.value = 'Tensiómetro digital';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);

    const cambio = http.expectOne({ method: 'PATCH', url: URL });

    expect(cambio.request.body).toEqual({
      nombre: 'Tensiómetro digital',
      tecnologiaPredominante: 'Electrónica',
      definicionTecnica: 'Mide presión arterial',
      recomendacionesCuidado: 'No golpear',
    });
    expect(cambio.request.body).not.toHaveProperty('modalidadVerificacion');
    cambio.flush(TIPOS[0]);
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe('/catalogo');
  });

  afterEach(() => {
    http.verify();
    desinstalarAlmacenamiento();
  });
});
