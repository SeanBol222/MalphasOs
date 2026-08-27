package com.malphasos.malphasos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MalphasosApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void flywayAplicaLasMigracionesAlArrancar() {
		Integer aplicadas = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);

		assertThat(aplicadas).isPositive();
	}

	@Test
	void elBaselineHabilitaLaExtensionPgcrypto() {
		Integer extensiones = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_extension WHERE extname = 'pgcrypto'", Integer.class);

		assertThat(extensiones).isEqualTo(1);
	}

}
