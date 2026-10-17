import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { ID_REPORTE, URL_REPORTES } from '../../../../testing/reportes';
import { RecepcionDelEquipo } from './recepcion-del-equipo';

const URL_INGRESO = `${URL_REPORTES}/${ID_REPORTE}/intake`;

/** Cómo llegó el equipo: las dos verificaciones al ingreso y sus accesorios (V31, 2026-10-16). */
describe('Recepción del equipo', () => {
  let fixture: ComponentFixture<RecepcionDelEquipo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['report.write'] }),
      ],
    });
    fixture = TestBed.createComponent(RecepcionDelEquipo);
    fixture.componentRef.setInput('idReporte', ID_REPORTE);
    fixture.componentRef.setInput('editable', true);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => (raiz().textContent ?? '').replace(/\s+/g, ' ');
  const campo = (id: string) => raiz().querySelector<HTMLInputElement>(`#${id}`)!;
  const escribir = (id: string, valor: string) => {
    campo(id).value = valor;
    campo(id).dispatchEvent(new Event('input'));
  };
  const marcar = (id: string) => campo(id).click();
  const boton = (texto: string) =>
    [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
    )!;

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
  }

  it('tiene las dos filas fijas del informe, con sus tres estados', async () => {
    await abrir();

    expect(raiz().querySelectorAll('[data-ingreso]')).toHaveLength(2);
    expect(texto()).toContain('Estado de la estructura física del equipo');
    expect(texto()).toContain('Estado operativo del equipo');
    expect(raiz().querySelectorAll('[data-ingreso="fisico"] input[type="radio"]')).toHaveLength(3);
  });

  it('guarda las dos verificaciones y los accesorios en su orden, recortados', async () => {
    await abrir();
    marcar('ingreso-fisico-NO_CONFORME');
    escribir('ingreso-fisico-observacion', ' Carcasa rayada ');
    marcar('ingreso-operativo-CONFORME');
    boton('Añadir accesorio').click();
    boton('Añadir accesorio').click();
    await asentar(fixture);
    escribir('accesorio-nombre-0', ' Sonda ');
    escribir('accesorio-nombre-1', 'Cable');
    raiz()
      .querySelectorAll<HTMLInputElement>('[data-accesorio="1"] input[type="checkbox"]')[0]
      .click();
    escribir('accesorio-observacion-1', 'Pelado');
    await asentar(fixture);
    boton('Guardar recepción').click();
    await asentar(fixture);

    const peticion = http.expectOne(URL_INGRESO);
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body).toEqual({
      fisico: 'NO_CONFORME',
      observacionFisico: 'Carcasa rayada',
      operativo: 'CONFORME',
      observacionOperativo: undefined,
      accesorios: [
        { nombre: 'Sonda', conforme: true, observacion: undefined },
        { nombre: 'Cable', conforme: false, observacion: 'Pelado' },
      ],
    });
    peticion.flush({ id: ID_REPORTE });
    await asentar(fixture);
  });

  it('sin las dos verificaciones, o no conforme sin observación, no manda nada y dice qué falta', async () => {
    await abrir();
    marcar('ingreso-fisico-NO_CONFORME');
    boton('Guardar recepción').click();
    await asentar(fixture);

    http.expectNone(URL_INGRESO);
    expect(texto()).toContain('si no es conforme, diga qué estaba mal');
    expect(texto()).toContain('Falta decir el estado operativo del equipo');
  });

  it('un accesorio no conforme sin observación, o sin nombre, tampoco', async () => {
    await abrir();
    marcar('ingreso-fisico-CONFORME');
    marcar('ingreso-operativo-CONFORME');
    boton('Añadir accesorio').click();
    boton('Añadir accesorio').click();
    await asentar(fixture);
    escribir('accesorio-nombre-0', 'Sonda');
    raiz().querySelector<HTMLInputElement>('[data-accesorio="0"] input[type="checkbox"]')!.click();
    await asentar(fixture);
    boton('Guardar recepción').click();
    await asentar(fixture);

    http.expectNone(URL_INGRESO);
    expect(texto()).toContain('Sonda: si no es conforme, diga por qué');
    expect(texto()).toContain('El accesorio 2 no tiene nombre');
  });

  it('quitar un accesorio lo saca de lo que se manda', async () => {
    await abrir();
    marcar('ingreso-fisico-NO_APLICA');
    marcar('ingreso-operativo-CONFORME');
    boton('Añadir accesorio').click();
    await asentar(fixture);
    escribir('accesorio-nombre-0', 'Sonda');
    boton('Quitar el accesorio 1').click();
    await asentar(fixture);
    boton('Guardar recepción').click();
    await asentar(fixture);

    const peticion = http.expectOne(URL_INGRESO);
    expect(peticion.request.body.accesorios).toEqual([]);
    peticion.flush({ id: ID_REPORTE });
    await asentar(fixture);
  });

  it('muestra lo guardado, y en un reporte cerrado no se toca', async () => {
    fixture.componentRef.setInput('editable', false);
    fixture.componentRef.setInput('ingresoFisico', {
      estado: 'NO_CONFORME',
      observacion: 'Rayado',
    });
    fixture.componentRef.setInput('ingresoOperativo', { estado: 'CONFORME' });
    fixture.componentRef.setInput('accesorios', [{ orden: 1, nombre: 'Sonda', conforme: true }]);
    await abrir();

    expect(campo('ingreso-fisico-NO_CONFORME').checked).toBe(true);
    expect(campo('ingreso-fisico-observacion').value).toBe('Rayado');
    expect(campo('ingreso-operativo-CONFORME').checked).toBe(true);
    expect(campo('accesorio-nombre-0').value).toBe('Sonda');
    expect(campo('accesorio-nombre-0').disabled).toBe(true);
    expect(campo('ingreso-fisico-CONFORME').disabled).toBe(true);
    expect(boton('Guardar recepción')).toBeUndefined();
    expect(boton('Quitar el accesorio 1')).toBeUndefined();
  });

  it('sin accesorios dice que llegó sin ellos', async () => {
    fixture.componentRef.setInput('editable', false);
    await abrir();

    expect(texto()).toContain('Llegó sin accesorios.');
  });
});
