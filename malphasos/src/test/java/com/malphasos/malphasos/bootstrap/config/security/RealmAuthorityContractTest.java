package com.malphasos.malphasos.bootstrap.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato entre el vocabulario de {@link ApiAuthority} y los roles que el realm define de verdad.
 *
 * <p>Las dos listas tienen que decir lo mismo, y ninguna de las dos avisa cuando dejan de hacerlo.
 * Si el realm gana un rol que {@code ApiAuthority} no conoce, el administrador no lo recibirá al
 * expandirse y solo lo notará quien intente usar ese endpoint. Si {@code ApiAuthority} nombra un rol
 * que el realm no define, ese permiso no lo puede traer nadie: el endpoint queda cerrado para
 * siempre. Las dos averías son silenciosas y se descubren en producción.
 */
class RealmAuthorityContractTest {

    /** Todo lo que la aplicación conoce: los recursos más quienes los conceden. */
    private static Set<String> vocabularioDeLaAplicacion() {
        Set<String> todo = new LinkedHashSet<>(ApiAuthority.RESOURCE_AUTHORITIES);
        todo.addAll(ApiAuthority.SUPER_AUTHORITIES);
        todo.add(ApiAuthority.ADMIN_FULL);
        todo.add(ApiAuthority.SUPER_ADMIN_FULL);

        return todo;
    }

    @Test
    @DisplayName("el realm define exactamente los roles que ApiAuthority conoce, ni uno mas ni uno menos")
    void elRealmYElCatalogoDicenLoMismo() {
        assertThat(RealmFixture.rolesDelClientApi())
                .containsExactlyInAnyOrderElementsOf(vocabularioDeLaAplicacion());
    }

    @Test
    @DisplayName("ningun grupo trae una autoridad de super: se asignan a mano")
    void elEscalonDeArribaNoLoDaNingunGrupo() {
        // Un super usuario se crea entrando a Keycloak, no dandole de alta por el API. Si alguien
        // colgara super.person.write de un grupo, cualquiera que entrase en ese grupo podria crear
        // administradores, que es exactamente lo que el escalon existe para impedir.
        assertThat(RealmFixture.grupos())
                .allSatisfy(grupo -> assertThat(RealmFixture.rolesDelGrupo(grupo))
                        .doesNotContainAnyElementsOf(ApiAuthority.SUPER_AUTHORITIES));
    }

    @Test
    @DisplayName("el grupo de administradores trae admin.full y las diecinueve autoridades de recurso")
    void elGrupoDeAdministradoresLoTraeTodo() {
        Set<String> esperado = new LinkedHashSet<>(ApiAuthority.RESOURCE_AUTHORITIES);
        esperado.add(ApiAuthority.ADMIN_FULL);

        assertThat(RealmFixture.rolesDelGrupo("admins")).containsExactlyInAnyOrderElementsOf(esperado);
    }

    @Test
    @DisplayName("ningun grupo del realm recibe super.admin.full")
    void nadieRecibeSuperAdminPorGrupo() {
        // Se concede a mano, usuario por usuario. Que ningun grupo lo lleve es deliberado y conviene
        // que se rompa la prueba si alguien lo mete en uno, porque seria una escalada silenciosa.
        assertThat(RealmFixture.grupos())
                .allSatisfy(grupo -> assertThat(RealmFixture.rolesDelGrupo(grupo))
                        .doesNotContain(ApiAuthority.SUPER_ADMIN_FULL));
    }

    @Test
    @DisplayName("los roles de todo grupo pertenecen al vocabulario de la aplicacion")
    void ningunGrupoTraeUnRolQueLaAplicacionNoConoce() {
        assertThat(RealmFixture.grupos())
                .allSatisfy(grupo -> assertThat(RealmFixture.rolesDelGrupo(grupo))
                        .isSubsetOf(vocabularioDeLaAplicacion()));
    }

    @Test
    @DisplayName("el grupo de ingenieros lee todo, escribe equipos y no toca nada mas")
    void elPerfilDelIngeniero() {
        assertThat(RealmFixture.rolesDelGrupo("engineers"))
                .containsExactlyInAnyOrder(
                        ApiAuthority.PERSON_READ,
                        ApiAuthority.LOCATION_READ,
                        ApiAuthority.CLIENT_READ,
                        ApiAuthority.SERVICE_AREA_READ,
                        ApiAuthority.ENGINEER_READ,
                        ApiAuthority.EQUIPMENT_READ,
                        ApiAuthority.EQUIPMENT_WRITE,
                        ApiAuthority.EQUIPMENT_ASSIGN,
                        ApiAuthority.WORK_ORDER_READ,
                        ApiAuthority.WORK_ORDER_WRITE,
                        ApiAuthority.WORK_ORDER_ASSIGN,
                        // El ingeniero es quien llena el reporte en campo, asi que escribe reportes
                        // aunque no escriba clientes ni sedes.
                        ApiAuthority.REPORT_READ,
                        ApiAuthority.REPORT_WRITE);
    }

    @Test
    @DisplayName("el grupo de clientes solo lee, y solo lo suyo")
    void elPerfilDelCliente() {
        assertThat(RealmFixture.rolesDelGrupo("clients"))
                .containsExactlyInAnyOrder(
                        ApiAuthority.CLIENT_READ,
                        ApiAuthority.SERVICE_AREA_READ,
                        ApiAuthority.EQUIPMENT_READ,
                        ApiAuthority.WORK_ORDER_READ,
                        // El cliente lee los reportes de sus equipos; llenarlos no es suyo.
                        ApiAuthority.REPORT_READ);
    }

    @Test
    @DisplayName("ni ingenieros ni clientes reciben ninguna autoridad de escritura ajena a su oficio")
    void nadieSalvoElAdministradorEscribeFueraDeSuOficio() {
        Set<String> vedadas = Set.of(
                ApiAuthority.ADMIN_FULL,
                ApiAuthority.PERSON_WRITE,
                ApiAuthority.LOCATION_WRITE,
                ApiAuthority.CLIENT_WRITE,
                ApiAuthority.CLIENT_DELETE,
                ApiAuthority.SERVICE_AREA_WRITE,
                ApiAuthority.ENGINEER_ASSIGN);

        assertThat(RealmFixture.rolesDelGrupo("engineers")).doesNotContainAnyElementsOf(vedadas);
        assertThat(RealmFixture.rolesDelGrupo("clients")).doesNotContainAnyElementsOf(vedadas);
        // report.write no esta en la lista de vedadas a proposito: el ingeniero si la tiene, porque
        // llenar el reporte es su oficio. El cliente no, y eso se comprueba aparte.
        assertThat(RealmFixture.rolesDelGrupo("clients")).doesNotContain(ApiAuthority.REPORT_WRITE);
    }
}
