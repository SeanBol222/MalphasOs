import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { ID_REPORTE, URL_REPORTES } from '../../../../testing/reportes';
import { By } from '@angular/platform-browser';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { ReporteApi } from '../reporte-api';
import { LienzoDeFirma } from '../../../shared/lienzo-de-firma/lienzo-de-firma';
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

  it('en un reporte cerrado y firmado muestra lo guardado sin dejar tocarlo', async () => {
    // Decía «en un reporte cerrado»: desde V39 uno cerrado sin firma deja escribir quién firma.
    fixture.componentRef.setInput('editable', false);
    fixture.componentRef.setInput('recibe', { nombre: 'Ana Pérez', cargo: 'Jefe' });
    fixture.componentRef.setInput('firmadoRecibe', '2026-10-17T09:30:00');
    await abrir();

    expect(campo('recibeNombre').value).toBe('Ana Pérez');
    expect(campo('recibeNombre').disabled).toBe(true);
    expect(guardar()).toBeUndefined();
  });

  it('retirado y sin registrar, lo dice', async () => {
    // Decía «cerrado»: desde V39 uno cerrado sin firma todavía se firma, y pide el nombre.
    fixture.componentRef.setInput('editable', false);
    fixture.componentRef.setInput('puedeFirmar', false);
    await abrir();

    expect(raiz().textContent).toContain('No se registró quién recibió el equipo.');
    expect(raiz().querySelector('#recibeNombre')).toBeNull();
  });

  describe('La firma de quien recibe (V39, RF-18 y RF-21)', () => {
    const URL_FIRMA = `${URL_REPORTES}/${ID_REPORTE}/receipt-signature`;
    const PNG = 'data:image/png;base64,iVBORw0KGgo=';

    const firmar = () => raiz().querySelector<HTMLButtonElement>('[data-boton-firmar]');
    const dibujar = (imagen: string | null) =>
      (
        fixture.debugElement.query(By.directive(LienzoDeFirma)).componentInstance as LienzoDeFirma
      ).cambio.emit(imagen);

    it('firma con el nombre escrito y el trazo, sin la casilla, solo este reporte', async () => {
      await abrir();
      escribir('recibeNombre', ' Ana Pérez ');
      escribir('recibeCargo', 'Jefe de enfermería');
      dibujar(PNG);
      await asentar(fixture);
      firmar()!.click();
      await asentar(fixture);

      const peticion = http.expectOne(URL_FIRMA);
      expect(peticion.request.method).toBe('PATCH');
      expect(peticion.request.body).toEqual({
        nombre: 'Ana Pérez',
        cedula: undefined,
        cargo: 'Jefe de enfermería',
        imagen: PNG,
        aplicarALaOrden: false,
      });
      peticion.flush([{ id: ID_REPORTE }]);
      await asentar(fixture);

      expect(raiz().textContent).not.toContain('Firmados');
    });

    it('firmar deja caducados los reportes de la orden y el informe: con la casilla cambiaron otros', async () => {
      const cache = TestBed.inject(QueryClient);
      // El cliente de pruebas tiene gcTime 0: sin esto, lo sembrado desaparece antes de firmar.
      cache.setQueryDefaults(ReporteApi.CLAVE, { gcTime: Infinity });
      const DE_LA_ORDEN = ReporteApi.claveDeOrden('una-orden');
      const INFORME = [...ReporteApi.claveDetalle(ID_REPORTE), 'informe'];
      cache.setQueryData(DE_LA_ORDEN, []);
      cache.setQueryData(INFORME, {});
      await abrir();
      escribir('recibeNombre', 'Ana Pérez');
      dibujar(PNG);
      await asentar(fixture);
      firmar()!.click();
      await asentar(fixture);

      http.expectOne(URL_FIRMA).flush([{ id: ID_REPORTE }]);
      await asentar(fixture);

      expect(cache.getQueryState(DE_LA_ORDEN)?.isInvalidated).toBe(true);
      expect(cache.getQueryState(INFORME)?.isInvalidated).toBe(true);
    });

    it('con la casilla, la firma va a la orden y dice cuántos se firmaron', async () => {
      await abrir();
      escribir('recibeNombre', 'Ana Pérez');
      raiz().querySelector<HTMLInputElement>('[data-aplicar-orden]')!.click();
      dibujar(PNG);
      await asentar(fixture);
      firmar()!.click();
      await asentar(fixture);

      const peticion = http.expectOne(URL_FIRMA);
      expect(peticion.request.body.aplicarALaOrden).toBe(true);
      peticion.flush([{ id: ID_REPORTE }, { id: 'b' }, { id: 'c' }]);
      await asentar(fixture);

      expect(raiz().textContent).toContain('Firmados 3 reportes de esta orden.');
    });

    it('sin trazo no se firma, y dibujar quita el aviso', async () => {
      await abrir();
      escribir('recibeNombre', 'Ana Pérez');
      await asentar(fixture);
      firmar()!.click();
      await asentar(fixture);

      http.expectNone(URL_FIRMA);
      expect(raiz().textContent).toContain('Dibuje la firma antes de guardarla.');

      dibujar(PNG);
      await asentar(fixture);
      expect(raiz().textContent).not.toContain('Dibuje la firma antes de guardarla.');
    });

    it('limpiar el lienzo deja otra vez sin firma', async () => {
      await abrir();
      escribir('recibeNombre', 'Ana Pérez');
      dibujar(PNG);
      dibujar(null);
      await asentar(fixture);
      firmar()!.click();
      await asentar(fixture);

      http.expectNone(URL_FIRMA);
      expect(raiz().textContent).toContain('Dibuje la firma antes de guardarla.');
    });

    it('sin nombre no se firma: no se sabría quién firmó', async () => {
      await abrir();
      dibujar(PNG);
      await asentar(fixture);
      firmar()!.click();
      await asentar(fixture);

      http.expectNone(URL_FIRMA);
      expect(raiz().textContent).toContain('Escriba el nombre de quien firma.');
    });

    it('un reporte cerrado sin firma deja escribir quién firma y firmar, pero no guardar la entrega', async () => {
      fixture.componentRef.setInput('editable', false);
      await abrir();

      expect(campo('recibeNombre').disabled).toBe(false);
      expect(firmar()).not.toBeNull();
      expect(guardar()).toBeUndefined();
    });

    it('firmado dice cuándo, no ofrece el lienzo ni deja cambiar el nombre, aun en borrador', async () => {
      fixture.componentRef.setInput('recibe', { nombre: 'Ana Pérez' });
      fixture.componentRef.setInput('firmadoRecibe', '2026-10-17T09:30:00');
      await abrir();

      expect(raiz().querySelector('[data-firmado]')?.textContent).toContain('17 oct 2026');
      expect(raiz().querySelector('app-lienzo-de-firma')).toBeNull();
      expect(campo('recibeNombre').disabled).toBe(true);
      expect(guardar()).toBeUndefined();
    });

    it('un reporte retirado no se firma', async () => {
      fixture.componentRef.setInput('editable', false);
      fixture.componentRef.setInput('puedeFirmar', false);
      fixture.componentRef.setInput('recibe', { nombre: 'Ana Pérez' });
      await abrir();

      expect(firmar()).toBeNull();
      expect(campo('recibeNombre').disabled).toBe(true);
    });

    it('si el servidor lo rechaza porque ya estaba firmado, lo dice con esas palabras', async () => {
      // El mensaje del 409 de los reportes habla de la orden y del cierre: aquí no diría nada útil.
      await abrir();
      escribir('recibeNombre', 'Ana Pérez');
      dibujar(PNG);
      await asentar(fixture);
      firmar()!.click();
      await asentar(fixture);

      http
        .expectOne(URL_FIRMA)
        .flush(
          { code: 'ERR_SERVICE_REPORT_003', details: ['Este reporte ya tiene la firma'] },
          { status: 409, statusText: 'Conflict' },
        );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'Este reporte ya tiene la firma de quien recibe',
      );
    });
  });
});
