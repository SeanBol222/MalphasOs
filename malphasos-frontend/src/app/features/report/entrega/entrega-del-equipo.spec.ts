import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { ID_REPORTE, URL_REPORTES } from '../../../../testing/reportes';
import { EntregaDelEquipo } from './entrega-del-equipo';

const URL_ENTREGA = `${URL_REPORTES}/${ID_REPORTE}/handover`;

/** A quién se entregó el equipo (V31, 2026-10-16). */
describe('Entrega del equipo', () => {
  let fixture: ComponentFixture<EntregaDelEquipo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['report.write'] }),
      ],
    });
    fixture = TestBed.createComponent(EntregaDelEquipo);
    fixture.componentRef.setInput('idReporte', ID_REPORTE);
    fixture.componentRef.setInput('editable', true);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const campo = (id: string) => raiz().querySelector<HTMLInputElement>(`#${id}`)!;
  const escribir = (id: string, valor: string) => {
    campo(id).value = valor;
    campo(id).dispatchEvent(new Event('input'));
  };
  const guardar = () =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Guardar entrega');

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
  }

  it('guarda nombre, cédula y cargo recortados', async () => {
    await abrir();
    escribir('recibeNombre', ' Ana Pérez ');
    escribir('recibeCedula', '52123456');
    escribir('recibeCargo', ' Jefe de enfermería ');
    await asentar(fixture);
    guardar()!.click();
    await asentar(fixture);

    const peticion = http.expectOne(URL_ENTREGA);
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body).toEqual({
      nombre: 'Ana Pérez',
      cedula: '52123456',
      cargo: 'Jefe de enfermería',
    });
    peticion.flush({ id: ID_REPORTE });
    await asentar(fixture);
  });

  it('cédula sin nombre no se manda: no dice quién recibió', async () => {
    await abrir();
    escribir('recibeCedula', '52123456');
    await asentar(fixture);
    guardar()!.click();
    await asentar(fixture);

    http.expectNone(URL_ENTREGA);
    expect(raiz().textContent).toContain('Falta el nombre');
  });

  it('los tres en blanco se mandan vacíos: así se borra', async () => {
    fixture.componentRef.setInput('recibe', { nombre: 'Ana Pérez' });
    await abrir();
    escribir('recibeNombre', '  ');
    await asentar(fixture);
    guardar()!.click();
    await asentar(fixture);

    const peticion = http.expectOne(URL_ENTREGA);
    expect(peticion.request.body).toEqual({
      nombre: undefined,
      cedula: undefined,
      cargo: undefined,
    });
    peticion.flush({ id: ID_REPORTE });
    await asentar(fixture);
  });

  it('en un reporte cerrado muestra lo guardado sin dejar tocarlo', async () => {
    fixture.componentRef.setInput('editable', false);
    fixture.componentRef.setInput('recibe', { nombre: 'Ana Pérez', cargo: 'Jefe' });
    await abrir();

    expect(campo('recibeNombre').value).toBe('Ana Pérez');
    expect(campo('recibeNombre').disabled).toBe(true);
    expect(guardar()).toBeUndefined();
  });

  it('cerrado y sin registrar, lo dice', async () => {
    fixture.componentRef.setInput('editable', false);
    await abrir();

    expect(raiz().textContent).toContain('No se registró quién recibió el equipo.');
    expect(raiz().querySelector('#recibeNombre')).toBeNull();
  });
});
