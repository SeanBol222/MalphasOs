import { Opcion } from '../../../shared/buscador/buscador';
import { TipoDeEquipo } from '../../../core/api/tipos';

/**
 * Las tecnologias autorizadas para describir un tipo de equipo.
 *
 * <p><b>Es una lista cerrada</b>, por decision del usuario: no se admite una tecnologia que no este
 * aqui. El backend la guarda como texto y aceptaria cualquier cosa, de modo que la restriccion vive
 * solo en el frontend — es una decision de vocabulario, no una regla de negocio del servidor.
 *
 * <p>Lo que la restriccion evita es real: con texto libre, la misma tecnologia acaba escrita de cuatro
 * maneras —«electronica», «Electrónica», «ELECTRONICO», «electro»— y agrupar por ella deja de servir.
 *
 * <p><b>Lo ya usado cuenta como autorizado, y va primero.</b> Dos razones: si esta empresa llama a algo
 * «Electromédica», eso vale mas que cualquier lista escrita de antemano; y sin ello, abrir un tipo
 * registrado con una tecnologia que no este en la lista dejaria el campo en blanco y el formulario
 * invalido sin explicar nada.
 *
 * <p><b>Para autorizar una tecnologia nueva se edita este archivo.</b> No hay pantalla que lo haga, y
 * es coherente con que sea vocabulario y no dato: un desplegable donde cualquiera anade valores no es
 * una lista cerrada.
 */

/**
 * Las autorizadas de partida.
 *
 * <p>Son tecnologias de equipo biomedico, no una taxonomia oficial: si a esta empresa le sirven otras,
 * se cambian aqui y no hay migracion que hacer, porque no son datos.
 */
const AUTORIZADAS: readonly string[] = [
  'Electrónica',
  'Electromecánica',
  'Electromédica',
  'Mecánica',
  'Neumática',
  'Hidráulica',
  'Óptica',
  'Digital',
  'Analógica',
  'Ultrasonido',
  'Rayos X',
  'Láser',
];

/**
 * Lo ya usado por los tipos registrados, seguido de las autorizadas de partida que falten.
 *
 * <p>Se compara sin tildes y sin mayusculas para no ofrecer «Electronica» y «Electrónica» como dos
 * cosas distintas, pero <b>se conserva la grafia que la empresa ya usa</b>: si alguien registro
 * «electronica», es esa la que se ofrece, porque es la que agrupa con lo que hay.
 *
 * <p>El identificador de cada opcion <b>es su nombre</b>: el backend guarda texto, y asi el formulario
 * envia la tecnologia tal cual sin dejar de rechazar lo que no este autorizado.
 */
export function tecnologiasAutorizadas(tipos: readonly TipoDeEquipo[] | undefined): readonly Opcion[] {
  const vistas = new Set<string>();
  const sugerencias: Opcion[] = [];

  const agregar = (valor: string | undefined) => {
    const limpio = (valor ?? '').trim();
    const clave = normalizar(limpio);

    if (!limpio || vistas.has(clave)) {
      return;
    }

    vistas.add(clave);
    // id y etiqueta iguales: lo que se guarda es el texto, y lo que se valida es que este en la lista.
    sugerencias.push({ id: limpio, etiqueta: limpio });
  };

  for (const tipo of tipos ?? []) {
    agregar(tipo.tecnologiaPredominante);
  }

  for (const tecnologia of AUTORIZADAS) {
    agregar(tecnologia);
  }

  return sugerencias;
}

function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}
