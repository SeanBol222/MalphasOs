package com.malphasos.malphasos.location.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.country.Country;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Ejercita los adaptadores contra un PostgreSQL real. Lo que se comprueba aquí es el viaje de ida y
 * vuelta entre el agregado y su fila, que los mappers escritos a mano hacen sin ayuda de MapStruct.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {"DELETE FROM ciudad", "DELETE FROM pais"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class LocationPersistenceAdapterTest {

    @Autowired private CountryPersistenceAdapter countryAdapter;
    @Autowired private CityPersistenceAdapter cityAdapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** El codigo ISO y el nombre son unicos: cada prueba necesita los suyos. */
    private Country unPais() {
        long n = System.nanoTime();
        String letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String iso = "" + letras.charAt((int) (n % 26))
                + letras.charAt((int) ((n / 26) % 26))
                + letras.charAt((int) ((n / 676) % 26));

        return Country.create(iso, "Pais " + n);
    }

    @Test
    @DisplayName("un pais guardado se recupera con todos sus campos")
    void idaYVueltaDeUnPais() {
        Country guardado = countryAdapter.save(unPais());

        Country recuperado = countryAdapter.findById(guardado.getId()).orElseThrow();

        assertThat(recuperado.getId()).isEqualTo(guardado.getId());
        assertThat(recuperado.getCodigoIso()).isEqualTo(guardado.getCodigoIso());
        assertThat(recuperado.getNombre()).isEqualTo(guardado.getNombre());
        assertThat(recuperado.isEstadoActivo()).isTrue();
    }

    @Test
    @DisplayName("lo recuperado no trae eventos: leer de la base no es un hecho del dominio")
    void loRecuperadoNoTraeEventos() {
        Country guardado = countryAdapter.save(unPais());

        assertThat(countryAdapter.findById(guardado.getId()).orElseThrow().hasPendingEvents())
                .isFalse();
    }

    @Test
    @DisplayName("retirar un pais conserva la fila con el estado en falso")
    void retirarConservaLaFila() {
        Country pais = countryAdapter.save(unPais());
        pais.deactivate();
        countryAdapter.save(pais);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT b_estado_activo FROM pais WHERE k_id_pais = ?",
                        Boolean.class, pais.getId()))
                .isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pais WHERE k_id_pais = ?", Integer.class, pais.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("guardar dos veces actualiza la fila en vez de crear otra")
    void guardarDosVecesNoDuplica() {
        Country pais = countryAdapter.save(unPais());
        pais.rename("Nombre cambiado " + System.nanoTime());
        countryAdapter.save(pais);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pais WHERE k_codigo_iso = ?",
                        Integer.class, pais.getCodigoIso()))
                .isEqualTo(1);
        assertThat(countryAdapter.findById(pais.getId()).orElseThrow().getNombre())
                .isEqualTo(pais.getNombre());
    }

    @Test
    @DisplayName("las ciudades se recuperan por su pais")
    void ciudadesPorPais() {
        Country colombia = countryAdapter.save(unPais());
        Country espana = countryAdapter.save(unPais());

        cityAdapter.save(City.create("Bogota", colombia.getId()));
        cityAdapter.save(City.create("Medellin", colombia.getId()));
        cityAdapter.save(City.create("Madrid", espana.getId()));

        assertThat(cityAdapter.findByCountry(colombia.getId()))
                .extracting(City::getNombre)
                .containsExactlyInAnyOrder("Bogota", "Medellin");
    }

    @Test
    @DisplayName("un pais que no existe no se encuentra, en vez de fallar")
    void paisInexistente() {
        assertThat(countryAdapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("trasladar una ciudad de pais persiste el cambio")
    void trasladarPersiste() {
        Country colombia = countryAdapter.save(unPais());
        Country espana = countryAdapter.save(unPais());

        City ciudad = cityAdapter.save(City.create("Cordoba", colombia.getId()));
        ciudad.relocateTo(espana.getId());
        cityAdapter.save(ciudad);

        assertThat(cityAdapter.findById(ciudad.getId()).orElseThrow().getIdPais())
                .isEqualTo(espana.getId());
    }
}
