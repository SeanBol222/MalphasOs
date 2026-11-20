import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { buscarConLaLupa } from '../../../../testing/catalogo';
import { HOY } from '../../../core/fechas';
import { calibracionAntesDelVencimiento, Patrones } from './patrones';
import { FormControl, FormGroup } from '@angular/forms';

const URL = 'http://localhost:8081/v1/api/reference-standards';

const PATRONES = [
  {
    id: 'p1',
    nombre: 'Termómetro patrón',
    marca: 'Fluke',
    modelo: '1524',
    serie: 'T-1',
    codigoInterno: 'BB-PAT-003',
    rango: '-50 a 150 °C',
    resolucion: '0,01 °C',
    certificado: 'C-1',
    empresaCalibradora: 'Metrología Andina',
    fechaCalibracion: '2026-01-31',
    vencimientoCertificado: '2027-01-31',
    estadoActivo: true,
  },
  { id: 'p2', nombre: 'Juego de pesas F1', serie: 'P-1', certificado: 'C-2', vencimientoCertificado: '2026-10-10', estadoActivo: true },
  { id: 'p3', nombre: 'Manómetro', marca: 'Wika', serie: 'M-1', certificado: 'C-3', vencimientoCertificado: '2026-11-01', estadoActivo: true },
  { id: 'p4', nombre: 'Barómetro viejo', serie: 'B-1', certificado: 'C-4', vencimientoCertificado: '2025-01-01', estadoActivo: false },
];

/** El catálogo de equipos patrón (2026-10-14). Hoy es el 14 de octubre de 2026. */
describe('Equipos patrón', () => {
  let fixture: ComponentFixture<Patrones>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = ['equipment.read', 'equipment.write']): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        { provide: HOY, useValue: () => new Date(2026, 9, 14, 9, 0) },
      ],
    });
    fixture = TestBed.createComponent(Patrones);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const ids = () => [...raiz().querySelectorAll('[data-patron]')].map((f) => f.getAttribute('data-patron'));
  const celdas = (id: string) =>
    [...raiz().querySelectorAll(`[data-patron="${id}"] td`)].map((td) =>
      td.textContent?.replace(/\s+/g, ' ').trim(),
    );
  const escribir = (id: string, valor: string) => {
    const campo = raiz().querySelector<HTMLInputElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  };
  const boton = (texto: string) =>
    [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
    ) as HTMLButtonElement;

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, PATRONES);
  }

  /** El formulario de alta, que desde el 2026-11-20 se abre con «Nuevo patrón». */
  function abrirAlta(): void {
    boton('Nuevo patrón').click();
    fixture.detectChanges();
  }

  it('por nombre, de la A a la Z, y la cabecera lo invierte', async () => {
    // Hasta el 2026-11-20 iban los vigentes primero, del que vence antes al que vence despues; desde
    // entonces el catalogo entero ordena por su primera columna, decidido por el usuario. Lo que urge
    // se sigue viendo por el peso de «Vencido» y «Por vencer».
    montar();
    await abrir();

    expect(ids()).toEqual(['p4', 'p2', 'p3', 'p1']);

    raiz().querySelector<HTMLButtonElement>('[data-ordenar]')!.click();
    await asentar(fixture);

    expect(ids()).toEqual(['p1', 'p3', 'p2', 'p4']);
  });

  it('busca por nombre, marca, serie o certificado, sin tildes ni mayusculas', async () => {
    montar();
    await abrir();

    await buscarConLaLupa(fixture, 'filtroPatrones', 'MANOMETRO');
    expect(ids()).toEqual(['p3']);

    await buscarConLaLupa(fixture, 'filtroPatrones', 'wika');
    expect(ids()).toEqual(['p3']);

    await buscarConLaLupa(fixture, 'filtroPatrones', 'c-2');
    expect(ids()).toEqual(['p2']);

    await buscarConLaLupa(fixture, 'filtroPatrones', 'desfibrilador');
    expect(raiz().textContent).toContain('Ningún patrón coincide con «desfibrilador»');
  });

  it('el formulario va en dos recuadros: el aparato a la izquierda, su calibración y los botones a la derecha', async () => {
    montar();
    await abrir();
    abrirAlta();
    const aparato = raiz().querySelector('[data-columna="aparato"]')!;
    const calibracion = raiz().querySelector('[data-columna="calibracion"]')!;

    for (const id of ['patronNombre', 'patronMarca', 'patronModelo', 'patronSerie', 'patronCodigo', 'patronRango', 'patronResolucion']) {
      expect(aparato.querySelector(`#${id}`)).not.toBeNull();
    }
    for (const id of ['patronCertificado', 'patronEmpresa', 'patronCalibrado', 'patronVence']) {
      expect(calibracion.querySelector(`#${id}`)).not.toBeNull();
    }
    const botones = [...calibracion.querySelectorAll('button')].map((b) => b.textContent?.trim());
    expect(botones).toEqual(['Registrar patrón', 'Cancelar']);
    // Dos recuadros separados, cada uno con su titulo del mismo tamano, y el del formulario dentro del
    // de la izquierda, mas grande.
    expect(aparato.classList).toContain('superficie');
    expect(calibracion.classList).toContain('superficie');
    expect(aparato.contains(calibracion)).toBe(false);
    expect(aparato.querySelector('h4.titulo-seccion')?.textContent?.trim()).toBe('El dispositivo');
    expect(calibracion.querySelector('h4.titulo-seccion')?.textContent?.trim()).toBe('Su calibración');
    const titulo = raiz().querySelector('#patron-formulario')!;
    expect(titulo.textContent?.trim()).toBe('Patrón nuevo');
    expect(titulo.className).toContain('text-2xl');
    expect(aparato.contains(titulo)).toBe(true);
  });

  it('el formulario no esta a la vista: se abre con «Nuevo patrón» o al editar, y se cierra al cancelar', async () => {
    montar();
    await abrir();
    expect(raiz().querySelector('#patronNombre')).toBeNull();

    abrirAlta();
    expect(raiz().querySelector('#patronNombre')).not.toBeNull();
    boton('Cancelar').click();
    fixture.detectChanges();
    expect(raiz().querySelector('#patronNombre')).toBeNull();

    boton('Editar Manómetro').click();
    fixture.detectChanges();
    expect(raiz().querySelector<HTMLInputElement>('#patronNombre')!.value).toBe('Manómetro');
  });

  it('dice si el certificado está vencido, por vencer o vigente, y pesa más cuanto más urge', async () => {
    montar();
    await abrir();

    expect(celdas('p2')[3]).toBe('10 oct 2026 Vencido');
    expect(celdas('p3')[3]).toBe('01 nov 2026 Por vencer');
    expect(celdas('p1')[3]).toBe('31 ene 2027 Vigente');
    const estado = (id: string) => raiz().querySelector(`[data-patron="${id}"] td:nth-child(4) span:last-child`)!;
    expect(estado('p2').className).toContain('font-extrabold');
    expect(estado('p3').className).toContain('font-bold');
    expect(estado('p1').className).toContain('font-normal');
  });

  it('el día del vencimiento todavía vale', async () => {
    montar();
    fixture.detectChanges();
    await responderA(fixture, http, URL, [{ ...PATRONES[0], id: 'hoy', vencimientoCertificado: '2026-10-14' }]);

    expect(celdas('hoy')[3]).toBe('14 oct 2026 Por vencer');
  });

  it('un patrón sin marca lo dice, y uno retirado también', async () => {
    montar();
    await abrir();

    expect(celdas('p2')[0]).toBe('Juego de pesas F1 Sin marca');
    expect(celdas('p4')[0]).toBe('Barómetro viejo Sin marca · Retirado');
  });

  it('la fila dice marca y modelo, el código interno y quién lo calibró, cuando los tiene', async () => {
    montar();
    await abrir();

    expect(celdas('p1').slice(0, 3)).toEqual([
      'Termómetro patrón Fluke 1524',
      'T-1 Cód. BB-PAT-003',
      'C-1 Metrología Andina',
    ]);
    expect(celdas('p2').slice(1, 3)).toEqual(['P-1', 'C-2']);
  });

  it('registrar manda la ficha recortada, y lo que quedó en blanco no viaja', async () => {
    montar();
    await abrir();
    abrirAlta();
    escribir('patronNombre', 'Higrómetro');
    escribir('patronModelo', ' HM-40 ');
    escribir('patronSerie', 'H-1');
    escribir('patronCodigo', '  ');
    escribir('patronRango', ' 10 a 90 %HR ');
    escribir('patronResolucion', '0,1 %HR');
    escribir('patronCertificado', 'C-9');
    escribir('patronEmpresa', ' Metrología Andina ');
    escribir('patronCalibrado', '2026-06-30');
    escribir('patronVence', '2027-06-30');
    await asentar(fixture);
    boton('Registrar patrón').click();
    await asentar(fixture);

    const alta = http.expectOne({ method: 'POST', url: URL });
    expect(alta.request.body).toEqual({
      nombre: 'Higrómetro',
      modelo: 'HM-40',
      serie: 'H-1',
      rango: '10 a 90 %HR',
      resolucion: '0,1 %HR',
      certificado: 'C-9',
      empresaCalibradora: 'Metrología Andina',
      fechaCalibracion: '2026-06-30',
      vencimientoCertificado: '2027-06-30',
    });
    expect('codigoInterno' in alta.request.body && alta.request.body.codigoInterno).toBe(undefined);
    alta.flush({ ...PATRONES[0], id: 'p9' });
    await asentar(fixture);
    http.match(URL).forEach((p) => p.flush(PATRONES));
  });

  it('un código interno ocupado se explica con el catálogo, distinto de la serie ocupada', async () => {
    // 2026-10-17: el código interno no se repite entre los vigentes (V35).
    montar();
    await abrir();
    abrirAlta();
    escribir('patronNombre', 'Higrómetro');
    escribir('patronSerie', 'H-1');
    escribir('patronCodigo', 'PT-01');
    escribir('patronCertificado', 'C-9');
    escribir('patronVence', '2027-06-30');
    await asentar(fixture);
    boton('Registrar patrón').click();
    await asentar(fixture);

    http
      .expectOne({ method: 'POST', url: URL })
      .flush(
        { code: 'ERR_EQUIPMENT_019', message: 'A reference standard with that internal code already exists' },
        { status: 409, statusText: 'Conflict' },
      );
    await asentar(fixture);

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
      'Ya hay un equipo patrón vigente con ese código interno.',
    );
  });

  it('una calibración posterior al vencimiento no llega al servidor, y lo dice aparte', async () => {
    montar();
    await abrir();
    abrirAlta();
    escribir('patronNombre', 'Higrómetro');
    escribir('patronSerie', 'H-1');
    escribir('patronCertificado', 'C-9');
    escribir('patronCalibrado', '2027-07-01');
    escribir('patronVence', '2027-06-30');
    boton('Registrar patrón').click();
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL });
    expect(raiz().textContent).toContain('no puede ser posterior al vencimiento');
    expect(raiz().textContent).not.toContain('son obligatorios');
  });

  it('calibrar el mismo día que vence vale, y sin fecha de calibración no hay nada que comparar', () => {
    const grupo = (fechaCalibracion: string, vencimientoCertificado: string) =>
      new FormGroup({
        fechaCalibracion: new FormControl(fechaCalibracion),
        vencimientoCertificado: new FormControl(vencimientoCertificado),
      });

    expect(calibracionAntesDelVencimiento(grupo('2027-06-30', '2027-06-30'))).toBeNull();
    expect(calibracionAntesDelVencimiento(grupo('', '2027-06-30'))).toBeNull();
    expect(calibracionAntesDelVencimiento(grupo('2027-07-01', '2027-06-30'))).toEqual({
      calibracionPosterior: true,
    });
  });

  it('registrar manda los cinco campos, sin marca si quedó en blanco', async () => {
    montar();
    await abrir();
    abrirAlta();
    escribir('patronNombre', ' Higrómetro ');
    escribir('patronSerie', 'H-1');
    escribir('patronCertificado', 'C-9');
    escribir('patronVence', '2027-06-30');
    await asentar(fixture);
    boton('Registrar patrón').click();
    await asentar(fixture);

    const alta = http.expectOne({ method: 'POST', url: URL });
    expect(alta.request.body).toEqual({
      nombre: 'Higrómetro',
      marca: undefined,
      serie: 'H-1',
      certificado: 'C-9',
      vencimientoCertificado: '2027-06-30',
    });
    alta.flush({ ...PATRONES[0], id: 'p9' });
    await asentar(fixture);
    http.match(URL).forEach((p) => p.flush(PATRONES));
  });

  it('sin vencimiento no llega al servidor y dice qué falta', async () => {
    montar();
    await abrir();
    abrirAlta();
    escribir('patronNombre', 'Higrómetro');
    escribir('patronSerie', 'H-1');
    escribir('patronCertificado', 'C-9');
    boton('Registrar patrón').click();
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL });
    expect(raiz().textContent).toContain('su vencimiento son obligatorios');
  });

  it('editar carga el patrón en el formulario y guarda por su ruta: así se renueva un certificado', async () => {
    montar();
    await abrir();
    boton('Editar Juego de pesas F1').click();
    await asentar(fixture);

    expect(raiz().querySelector<HTMLInputElement>('#patronSerie')!.value).toBe('P-1');
    // Sin ficha, sus campos quedan en blanco, no con lo del patrón anterior.
    expect(raiz().querySelector<HTMLInputElement>('#patronCodigo')!.value).toBe('');
    escribir('patronCertificado', 'C-2b');
    escribir('patronVence', '2027-10-10');
    boton('Guardar cambios').click();
    await asentar(fixture);

    const cambio = http.expectOne({ method: 'PATCH', url: `${URL}/p2` });
    expect(cambio.request.body.certificado).toBe('C-2b');
    expect(cambio.request.body.vencimientoCertificado).toBe('2027-10-10');
    cambio.flush({ ...PATRONES[1], certificado: 'C-2b' });
    await asentar(fixture);
    http.match(URL).forEach((p) => p.flush(PATRONES));
  });

  it('editar sube hasta el formulario, que se abre encima de la tabla', async () => {
    // Pedido el 2026-11-20: con la tabla larga, «Editar» en una fila de abajo dejaba el formulario
    // fuera de la vista.
    const original = Element.prototype.scrollIntoView;
    const subir = vi.fn();
    Element.prototype.scrollIntoView = subir;
    try {
      montar();
      await abrir();
      boton('Editar Manómetro').click();
      await asentar(fixture);

      expect(subir).toHaveBeenCalledTimes(1);
      const formulario = raiz().querySelector('[data-columna="aparato"]')!.parentElement;
      expect(subir.mock.contexts[0]).toBe(formulario);
    } finally {
      Element.prototype.scrollIntoView = original;
    }
  });

  it('editar uno con ficha la carga entera', async () => {
    montar();
    await abrir();
    boton('Editar Termómetro patrón').click();
    await asentar(fixture);

    const valor = (id: string) => raiz().querySelector<HTMLInputElement>(`#${id}`)!.value;
    expect(
      ['patronModelo', 'patronCodigo', 'patronRango', 'patronResolucion', 'patronEmpresa', 'patronCalibrado'].map(valor),
    ).toEqual(['1524', 'BB-PAT-003', '-50 a 150 °C', '0,01 °C', 'Metrología Andina', '2026-01-31']);
  });

  it('retirar pregunta antes, oculta «Editar» mientras tanto, y solo «Sí, retirar» retira', async () => {
    montar();
    await abrir();
    const botones = () =>
      [...raiz().querySelectorAll('[data-patron="p3"] td[data-acciones] button')].map((b) =>
        b.textContent?.trim(),
      );

    boton('Retirar Manómetro').click();
    await asentar(fixture);
    http.expectNone({ method: 'DELETE', url: `${URL}/p3` });
    expect(raiz().textContent).toContain('¿Seguro que quiere retirar?');
    expect(botones()).toEqual(['Sí, retirar', 'Cancelar']);

    boton('Sí, retirar').click();
    await asentar(fixture);
    http.expectOne({ method: 'DELETE', url: `${URL}/p3` }).flush(null);
    await asentar(fixture);
    // La recarga trae los patrones otra vez: si la pregunta se va es porque se cerró.
    http.match(URL).forEach((p) => p.flush(PATRONES));
    await asentar(fixture);
    expect(raiz().textContent).not.toContain('¿Seguro que quiere retirar');
  });

  it('sin equipment.write se ve y no se toca', async () => {
    montar(['equipment.read']);
    await abrir();

    expect(raiz().querySelector('#patronNombre')).toBeNull();
    expect(boton('Editar Juego de pesas F1')).toBeUndefined();
  });
});
