package com.malphasos.malphasos.bootstrap.config.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Vocabulario completo de autoridades del API y regla de expansión del
 * administrador.
 *
 * <p>
 * Los nombres son exactamente los roles que el realm de Keycloak define sobre
 * el client
 * {@code malphasos-api}. Esta clase no los inventa: los refleja. Si se añade un
 * rol al realm que
 * algún endpoint vaya a exigir, hay que añadirlo también aquí, porque de lo
 * contrario el
 * administrador no lo recibirá al expandirse.
 *
 * <p>
 * <b>Por qué existe la expansión.</b> Cada operación REST declara únicamente la
 * autoridad de su
 * recurso, por ejemplo {@code hasAuthority('client.read')}. Si además tuviera
 * que nombrar al
 * administrador —{@code hasAnyAuthority('admin.full','client.read')}— el modelo
 * de permisos
 * quedaría repetido en las ochenta y tres operaciones y bastaría olvidarse de
 * una para abrir un
 * agujero. En su lugar, quien trae {@code admin.full} recibe todas las
 * autoridades de recurso, y
 * quién es administrador se decide en un solo sitio: aquí.
 *
 * <p>
 * {@code super.admin.full} implica {@code admin.full} y, <b>desde el
 * 2026-09-13, algo más</b>.
 * Hasta esa fecha los dos concedían exactamente lo mismo y este javadoc decía
 * que se conservaban
 * separados por si «un realm futuro» daba al segundo capacidades propias. Ese
 * futuro llegó: las de
 * {@link #SUPER_AUTHORITIES} las concede solo el primero.
 *
 * <p>
 * La regla se lee en los nombres sin abrir el código: <b>lo que empieza por
 * {@code super.} es
 * justamente lo que el administrador no recibe al expandirse.</b>
 *
 * <p>
 * Este javadoc decía que las autoridades de {@code work-order} figuraban aquí
 * <b>aunque el módulo
 * no existiera todavía</b>, para que el día que aparecieran sus endpoints el
 * administrador no se
 * quedara fuera por olvido. Fue cierto hasta el 2026-09-13, cuando ese módulo
 * se construyó. <b>Ya no
 * hay ninguna autoridad en esa situación</b>: las dos de {@code report},
 * añadidas el 2026-09-27,
 * entraron a la vez que sus rutas, porque {@code RestAuthorizationCoverageTest}
 * exige que toda
 * autoridad del catálogo proteja algo.
 */
public final class ApiAuthority {

  /**
   * Concede todas las autoridades de recurso. Es el permiso del grupo
   * {@code admins}.
   */
  public static final String ADMIN_FULL = "admin.full";

  /** Implica {@link #ADMIN_FULL} y, por tanto, todo lo que este concede. */
  public static final String SUPER_ADMIN_FULL = "super.admin.full";

  /**
   * Dar de alta, editar y retirar a la gente de la casa: ingenieros y
   * administradores.
   *
   * <p>
   * El prefijo {@code super.} no es decorativo: <b>marca lo que
   * {@link #ADMIN_FULL} no
   * concede</b>. Un administrador crea representantes de cliente y encargados;
   * crear a otro
   * administrador o a un ingeniero está un escalón por encima.
   *
   * <p>
   * No se llama {@code engineer.write} a propósito. {@code engineer.read} y
   * {@code engineer.assign} ya existen y hablan del <i>encargado</i> —lo que el
   * código llama
   * {@code Manager} y la ERS «profesional responsable»—, de modo que ese nombre
   * ya está tomado
   * por otro concepto.
   */
  public static final String SUPER_PERSON_WRITE = "super.person.write";

  public static final String PERSON_READ = "person.read";
  public static final String PERSON_WRITE = "person.write";

  public static final String LOCATION_READ = "location.read";
  public static final String LOCATION_WRITE = "location.write";

  public static final String CLIENT_READ = "client.read";
  public static final String CLIENT_WRITE = "client.write";

  /**
   * Dar de baja al cliente entero. Los sub-recursos del cliente son
   * {@link #CLIENT_WRITE}.
   */
  public static final String CLIENT_DELETE = "client.delete";

  public static final String SERVICE_AREA_READ = "service-area.read";
  public static final String SERVICE_AREA_WRITE = "service-area.write";

  public static final String ENGINEER_READ = "engineer.read";
  public static final String ENGINEER_ASSIGN = "engineer.assign";

  public static final String EQUIPMENT_READ = "equipment.read";
  public static final String EQUIPMENT_WRITE = "equipment.write";

  /** Vincular un equipo a un área de servicio o trasladarlo a otra. */
  public static final String EQUIPMENT_ASSIGN = "equipment.assign";

  public static final String WORK_ORDER_READ = "work-order.read";
  public static final String WORK_ORDER_WRITE = "work-order.write";
  public static final String WORK_ORDER_ASSIGN = "work-order.assign";

  public static final String REPORT_READ = "report.read";

  /**
   * Abrir, llenar, verificar, cerrar y retirar un reporte de servicio.
   *
   * <p>
   * <b>No hay una tercera autoridad para cerrar</b>, al contrario que en las
   * órdenes de trabajo,
   * donde asignar tiene la suya. Quien llena el reporte es quien lo firma en
   * campo: separarlas
   * describiría un reparto de trabajo que no existe. La firma digital (RF-21)
   * traerá la suya.
   */
  public static final String REPORT_WRITE = "report.write";

  /**
   * Todo lo que el administrador recibe al expandirse. No se incluye a sí mismo
   * ni a
   * {@link #SUPER_ADMIN_FULL}: son quién manda, no lo que se manda.
   *
   * <p>
   * Conserva el orden de declaración —por módulo, y dentro de cada módulo de
   * menos a más
   * poder— para que las autoridades de un administrador y la jerarquía que se
   * deriva de ellas
   * salgan siempre iguales. Un orden estable no es un requisito funcional, pero
   * convierte un
   * volcado de autoridades en algo comparable entre dos ejecuciones.
   */
  public static final Set<String> RESOURCE_AUTHORITIES = Collections.unmodifiableSet(
      new LinkedHashSet<>(List.of(
          PERSON_READ,
          PERSON_WRITE,
          LOCATION_READ,
          LOCATION_WRITE,
          CLIENT_READ,
          CLIENT_WRITE,
          CLIENT_DELETE,
          SERVICE_AREA_READ,
          SERVICE_AREA_WRITE,
          ENGINEER_READ,
          ENGINEER_ASSIGN,
          EQUIPMENT_READ,
          EQUIPMENT_WRITE,
          EQUIPMENT_ASSIGN,
          WORK_ORDER_READ,
          WORK_ORDER_WRITE,
          WORK_ORDER_ASSIGN,
          REPORT_READ,
          REPORT_WRITE)));

  /**
   * Lo que solo {@link #SUPER_ADMIN_FULL} concede, y {@link #ADMIN_FULL} no.
   *
   * <p>
   * Hasta el 2026-09-13 los dos acababan concediendo exactamente lo mismo, y el
   * javadoc de
   * esta clase decía que se conservaban separados «porque un realm futuro podría
   * dar al segundo
   * capacidades que el primero no tenga». Este conjunto es ese futuro.
   *
   * <p>
   * <b>Ninguna de estas autoridades puede estar en
   * {@link #RESOURCE_AUTHORITIES}</b>, o el
   * administrador las recibiría al expandirse y el escalón desaparecería sin que
   * nada fallara.
   * Lo fija una prueba, porque es la clase de error que se comete añadiendo una
   * línea al sitio
   * que parece correcto.
   */
  public static final Set<String> SUPER_AUTHORITIES = Collections
      .unmodifiableSet(new LinkedHashSet<>(List.of(SUPER_PERSON_WRITE)));

  private ApiAuthority() {
  }

  /**
   * Aplica la regla de expansión sobre los roles que venían en el token.
   *
   * <p>
   * Devuelve un conjunto nuevo; no modifica la entrada. Los roles que no
   * pertenecen al
   * vocabulario se conservan tal cual: esta clase decide qué añade, no qué quita,
   * y filtrar
   * silenciosamente un rol que alguien acaba de crear en el realm sería un fallo
   * difícil de
   * diagnosticar.
   *
   * @param tokenRoles roles leídos del token, en el orden en que llegaron
   * @return los mismos roles más las autoridades que el administrador concede, si
   *         procede
   */
  public static Set<String> expand(Collection<String> tokenRoles) {

    Set<String> granted = new LinkedHashSet<>(tokenRoles);

    if (granted.contains(SUPER_ADMIN_FULL)) {
      granted.add(ADMIN_FULL);
      granted.addAll(SUPER_AUTHORITIES);
    }

    if (granted.contains(ADMIN_FULL)) {
      granted.addAll(RESOURCE_AUTHORITIES);
    }

    return granted;
  }
}
