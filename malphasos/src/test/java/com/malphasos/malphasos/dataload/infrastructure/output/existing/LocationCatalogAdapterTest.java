package com.malphasos.malphasos.dataload.infrastructure.output.existing;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.location.application.ports.output.CityPersistencePort;
import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.country.Country;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El catálogo de ubicación que la carga nombra, contra PostgreSQL.
 *
 * <p>Crea su propio país y no cuenta con la siembra de V7: otras clases borran países y ciudades después
 * de cada prueba, y una que dependiera de Colombia pasaría o fallaría según el orden de la batería. Así
 * falló la primera versión de esta, que en su clase sola pasaba.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LocationCatalogAdapterTest {

    @Autowired private LocationCatalogAdapter adaptador;
    @Autowired private CountryPersistencePort paises;
    @Autowired private CityPersistencePort ciudades;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Country unPais() {
        String iso = CodigoIsoLibre.en(jdbcTemplate);
        return paises.save(Country.create(iso, "País " + iso));
    }

    @Test
    @DisplayName("un pais del catalogo existe por su codigo ISO; uno retirado o inventado, no")
    void paises() {
        Country activo = unPais();
        Country retirado = unPais();
        retirado.deactivate();
        paises.save(retirado);

        assertThat(adaptador.countryExists(activo.getCodigoIso())).isTrue();
        assertThat(adaptador.countryExists(retirado.getCodigoIso())).isFalse();
        assertThat(adaptador.countryExists(CodigoIsoLibre.en(jdbcTemplate))).isFalse();
    }

    @Test
    @DisplayName("las ciudades activas de un pais traen sus nombres como estan escritos, con tildes")
    void ciudades() {
        Country pais = unPais();
        ciudades.save(City.create("Bogotá", pais.getId()));
        City retirada = ciudades.save(City.create("Medellín", pais.getId()));
        retirada.deactivate();
        ciudades.save(retirada);

        assertThat(adaptador.citiesOf(pais.getCodigoIso())).containsExactly("Bogotá");
        assertThat(adaptador.citiesOf(CodigoIsoLibre.en(jdbcTemplate))).isEmpty();
    }
}
