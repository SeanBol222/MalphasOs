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
		Integer applied = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);

		assertThat(applied).isPositive();
	}

	@Test
	void elBaselineHabilitaLaExtensionPgcrypto() {
		Integer extensions = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_extension WHERE extname = 'pgcrypto'", Integer.class);

		assertThat(extensions).isEqualTo(1);
	}

}
