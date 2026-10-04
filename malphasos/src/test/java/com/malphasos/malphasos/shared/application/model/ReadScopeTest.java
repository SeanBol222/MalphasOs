package com.malphasos.malphasos.shared.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que se prueba aquí no es un contenedor de identificadores, es la distinción que impide el
 * defecto: «ve todo» y «no ve nada» no se pueden confundir.
 */
class ReadScopeTest {

    @Test
    @DisplayName("sin restriccion alcanza a cualquier cliente, incluso a uno que nadie ha visto")
    void sinRestriccionAlcanzaATodo() {
        ReadScope alcance = ReadScope.sinRestriccion();

        assertThat(alcance.alcanzaATodo()).isTrue();
        assertThat(alcance.alcanza(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("un alcance restringido solo alcanza a los suyos")
    void restringidoAlcanzaSoloALosSuyos() {
        UUID suyo = UUID.randomUUID();
        UUID ajeno = UUID.randomUUID();

        ReadScope alcance = ReadScope.deClientes(Set.of(suyo));

        assertThat(alcance.alcanzaATodo()).isFalse();
        assertThat(alcance.alcanza(suyo)).isTrue();
        assertThat(alcance.alcanza(ajeno)).isFalse();
    }

    @Test
    @DisplayName("restringido al conjunto vacio no alcanza a nada, y eso es legitimo")
    void restringidoAVacioNoAlcanzaANada() {
        ReadScope alcance = ReadScope.deClientes(Set.of());

        assertThat(alcance.alcanzaATodo()).isFalse();
        assertThat(alcance.alcanza(UUID.randomUUID())).isFalse();
        assertThat(alcance.clientesVisibles()).isEmpty();
    }

    @Test
    @DisplayName("pedir la lista de un alcance sin restriccion lanza en vez de devolver vacio")
    void pedirLaListaSinRestriccionLanza() {
        // Es la prueba central de esta clase. Si devolviera el conjunto vacio, un adaptador que
        // construyera «WHERE id IN (...)» con el resultado dejaria sin datos a quien lo ve todo, y
        // si devolviera null el filtro se saltaria por un NullPointerException mal atrapado. Falla.
        assertThatThrownBy(() -> ReadScope.sinRestriccion().clientesVisibles())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alcanzaATodo()");
    }

    @Test
    @DisplayName("el alcance copia el conjunto recibido: cambiarlo despues no amplia lo que se ve")
    void elAlcanceEsUnValor() {
        UUID suyo = UUID.randomUUID();
        Set<UUID> mutable = new HashSet<>(Set.of(suyo));

        ReadScope alcance = ReadScope.deClientes(mutable);
        mutable.add(UUID.randomUUID());

        assertThat(alcance.clientesVisibles()).containsExactly(suyo);
    }

    @Test
    @DisplayName("dos alcances con los mismos clientes son iguales, y ninguno iguala al libre")
    void igualdadPorContenido() {
        UUID cliente = UUID.randomUUID();

        assertThat(ReadScope.deClientes(Set.of(cliente))).isEqualTo(ReadScope.deClientes(Set.of(cliente)));
        assertThat(ReadScope.deClientes(Set.of())).isNotEqualTo(ReadScope.sinRestriccion());
    }
}
