import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { ListaPersonas } from './lista-personas';

/**
 * El listado de personas. No tenía prueba propia hasta el rediseño del 2026-10-11: lo cubrían de
 * refilón las de las altas.
 */
const API = 'http://localhost:8081/v1/api';
const URL_PERSONAS = `${API}/persons`;
const URL_CLIENTES = `${API}/clients`;
const URL_RESUMEN_SEDES = `${API}/headquarters/summary`;
const URL_ENCARGADOS = `${API}/managers`;

const PERSONAS = [
  {
    identificador: 'p1',
    primerNombre: 'Laura',
    primerApellido: 'Méndez',
    cedula: '1018456321',
    tipoPersona: 'ADMIN',
    estadoActivo: true,
    emailPersonList: [
      { correoPersona: 'vieja@empresa.example', estadoActivo: false },
      { correoPersona: 'laura@empresa.example', estadoActivo: true },
    ],
  },
  {
    identificador: 'p2',
    primerNombre: 'Alan',
    primerApellido: 'Turing',
    cedula: '7778889990',
    tipoPersona: 'ENGINEER',
    estadoActivo: false,
  },
  {
    identificador: 'p3',
    primerNombre: 'Carolina',
    primerApellido: 'Vélez',
    cedula: '52887412',
    tipoPersona: 'CEO_CLIENT',
    estadoActivo: true,
  },
  {
    identificador: 'p4',
    primerNombre: 'Marta',
    primerApellido: 'Ríos',
    cedula: '39774102',
    tipoPersona: 'MANAGER',
    estadoActivo: true,
  },
  {
    identificador: 'p5',
    primerNombre: 'Julián',
    primerApellido: 'Castro',
    cedula: '80123998',
    tipoPersona: 'MANAGER',
    estadoActivo: true,
  },
  {
    identificador: 'p6',
    primerNombre: 'Root',
    primerApellido: 'Admin',
    cedula: '1',
    tipoPersona: 'SUPER_ADMIN',
    estadoActivo: true,
  },
  // Ingeniero y además encargado: el modelo admite dos funciones, y cuenta en los dos filtros.
  {
    identificador: 'p7',
    primerNombre: 'Andrés',
    primerApellido: 'Pardo',
    cedula: '1020774512',
    tipoPersona: 'ENGINEER',
    segundoTipoPersona: 'MANAGER',
    estadoActivo: true,
  },
];

const CLIENTES = [
  { id: 'c1', razonSocial: 'Clínica del Norte', representantes: ['p3'], estadoActivo: true },
];
const SEDES = [
  {
    id: 's1',
    nombre: 'Sede principal',
    idCliente: 'c1',
    abierta: true,
    areas: [{ id: 'a1', nombre: 'Laboratorio', abierta: true }],
  },
];
const ENCARGADOS = [
  { idPersona: 'p4', idsSede: [], idsAreaServicio: ['a1'], estadoActivo: true },
  { idPersona: 'p5', idsSede: ['s1'], idsAreaServicio: [], estadoActivo: true },
  // Retirada: la representante ya no responde de la sede, y no debe decirlo. Desde V41 la respuesta
  // solo trae lo activo, asi que llega sin nada a cargo.
  { idPersona: 'p3', idsSede: [], idsAreaServicio: [], estadoActivo: false },
];

/** Un administrador: ve todo y registra a la gente del cliente, no a la de la casa. */
const ADMIN = ['person.read', 'person.write', 'client.read', 'engineer.read'];

describe('Listado de personas', () => {
  let fixture: ComponentFixture<ListaPersonas>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = ADMIN): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(ListaPersonas);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent?.replace(/\s+/g, ' ') ?? '';
  const fila = (id: string) => raiz().querySelector<HTMLTableRowElement>(`[data-persona="${id}"]`);
  const celdas = (id: string) =>
    [...(fila(id)?.querySelectorAll('td') ?? [])].map((td) =>
      td.textContent?.replace(/\s+/g, ' ').trim(),
    );
  const ids = () =>
    [...raiz().querySelectorAll('[data-persona]')].map((f) => f.getAttribute('data-persona'));
  const boton = (empieza: string) =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(empieza))!;

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
    http.match(URL_PERSONAS).forEach((p) => p.flush(PERSONAS));
    http.match(URL_CLIENTES).forEach((p) => p.flush(CLIENTES));
    http.match((r) => r.url === URL_RESUMEN_SEDES).forEach((p) => p.flush(SEDES));
    http.match(URL_ENCARGADOS).forEach((p) => p.flush(ENCARGADOS));
    await asentar(fixture);
  }

  it('cada persona con sus iniciales, su correo vigente, su función y su cliente', async () => {
    montar();
    await abrir();

    expect(celdas('p1')).toEqual([
      'LM Laura Méndez laura@empresa.example',
      '1018456321',
      'Administrador',
      'De la casa',
      'Con cuenta',
      'Activa',
    ]);
    expect(celdas('p3').slice(2, 5)).toEqual([
      'Representante del cliente',
      'Clínica del Norte',
      'Con cuenta',
    ]);
  });

  it('un encargado dice de qué área o sede responde, y de qué cliente es', async () => {
    montar();
    await abrir();

    expect(celdas('p4').slice(2, 5)).toEqual([
      'Encargado del cliente · Laboratorio',
      'Clínica del Norte',
      'Sin cuenta',
    ]);
    expect(celdas('p5').slice(2, 4)).toEqual([
      'Encargado del cliente · Sede principal',
      'Clínica del Norte',
    ]);
  });

  it('quien esta a cargo de varias cosas las dice todas (V41)', async () => {
    // Desde el 2026-10-27 un encargado responde por varias sedes y areas del mismo cliente.
    montar();
    fixture.detectChanges();
    await asentar(fixture);
    http
      .match(URL_ENCARGADOS)
      .forEach((p) =>
        p.flush([
          { idPersona: 'p4', idsSede: ['s1'], idsAreaServicio: ['a1'], estadoActivo: true },
        ]),
      );
    await abrir();

    expect(celdas('p4')[2]).toBe('Encargado del cliente · Sede principal, Laboratorio');
  });

  it('las iniciales de la casa van en tinta y las del cliente en papel', async () => {
    montar();
    await abrir();

    expect(fila('p1')!.querySelector('[aria-hidden="true"]')!.className).toContain('bg-ink');
    expect(fila('p3')!.querySelector('[aria-hidden="true"]')!.className).toContain(
      'bg-neutral-200',
    );
  });

  it('el estado se marca por peso: la retirada no pesa', async () => {
    montar();
    await abrir();

    expect(fila('p1')!.querySelector('td:last-child')!.className).toContain('font-bold');
    expect(fila('p2')!.querySelector('td:last-child')!.className).not.toContain('font-bold');
  });

  describe('Filtrar', () => {
    it('cada botón dice cuántas hay, y el super usuario cuenta como administrador', async () => {
      montar();
      await abrir();

      expect(
        [...raiz().querySelectorAll('[role="group"] button')].map((b) => b.textContent?.trim()),
      ).toEqual([
        'Todas · 7',
        'Ingenieros · 2',
        'Administradores · 2',
        'Representantes · 1',
        'Encargados · 3',
      ]);
    });

    it('elegir una función deja solo esa, y se marca pulsada', async () => {
      montar();
      await abrir();
      boton('Encargados').click();
      await asentar(fixture);

      expect(ids()).toEqual(['p4', 'p5', 'p7']);
      expect(celdas('p7')[2]).toBe('Ingeniero · Encargado del cliente');
      expect(boton('Encargados').getAttribute('aria-pressed')).toBe('true');
    });

    it('el buscador encuentra por nombre sin tildes y por cédula', async () => {
      montar();
      await abrir();
      const entrada = raiz().querySelector<HTMLInputElement>('#buscarPersonas')!;

      entrada.value = 'velez';
      entrada.dispatchEvent(new Event('input'));
      await asentar(fixture);
      expect(ids()).toEqual(['p3']);

      entrada.value = '3977';
      entrada.dispatchEvent(new Event('input'));
      await asentar(fixture);
      expect(ids()).toEqual(['p4']);
    });
  });

  describe('Registrar persona', () => {
    const enlaces = () =>
      [...raiz().querySelectorAll('#altas-de-personas a')].map((a) => a.getAttribute('href'));

    it('despliega las altas que la escalera permite: un administrador, solo las del cliente', async () => {
      montar();
      await abrir();
      expect(raiz().querySelector('#altas-de-personas')).toBeNull();

      boton('Registrar persona').click();
      await asentar(fixture);

      expect(boton('Registrar persona').getAttribute('aria-expanded')).toBe('true');
      expect(enlaces()).toEqual(['/personas/nueva/representante', '/personas/nueva/encargado']);
    });

    it('con super.person.write ofrece también las de la casa', async () => {
      montar([...ADMIN, 'super.person.write']);
      await abrir();
      boton('Registrar persona').click();
      await asentar(fixture);

      expect(enlaces()).toEqual([
        '/personas/nueva/ingeniero',
        '/personas/nueva/administrador',
        '/personas/nueva/representante',
        '/personas/nueva/encargado',
      ]);
    });

    it('Escape lo cierra', async () => {
      montar();
      await abrir();
      boton('Registrar persona').click();
      await asentar(fixture);
      boton('Registrar persona').dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
      );
      await asentar(fixture);

      expect(raiz().querySelector('#altas-de-personas')).toBeNull();
    });

    it('sin ninguna de las dos autoridades no hay botón', async () => {
      montar(['person.read']);
      await abrir();

      expect(texto()).not.toContain('Registrar persona');
    });
  });

  it('sin client.read ni engineer.read no pide clientes, sedes ni encargados, ni pinta la columna', async () => {
    montar(['person.read']);
    fixture.detectChanges();
    await asentar(fixture);

    expect(http.match(URL_CLIENTES)).toHaveLength(0);
    expect(http.match((r) => r.url === URL_RESUMEN_SEDES)).toHaveLength(0);
    expect(http.match(URL_ENCARGADOS)).toHaveLength(0);
    await abrir();
    expect([...raiz().querySelectorAll('th')].map((th) => th.textContent?.trim())).not.toContain(
      'Cliente',
    );
  });
});
