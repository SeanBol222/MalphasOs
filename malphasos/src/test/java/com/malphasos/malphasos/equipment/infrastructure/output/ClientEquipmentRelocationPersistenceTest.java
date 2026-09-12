package com.malphasos.malphasos.equipment.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.CreateServiceAreaCommand;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RelocateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * La regla del traslado dentro del mismo cliente, ejercitada de punta a punta contra un PostgreSQL
 * real: {@code ClientEquipmentService} preguntando a {@code ServiceAreaService}, cada uno con su
 * propio almacén.
 *
 * <p>No repite lo que ya cubre {@code EquipmentChainServiceTest} con dobles. Lo que aporta este
 * viaje real es que la fila de {@code equipo_cliente} de verdad cambia de área, y que la respuesta
 * a "de qué cliente es esta área" viene de una sede distinta a la original sin que eso confunda la
 * comparación.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM marca",
            "DELETE FROM fabricante",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente",
            "DELETE FROM ciudad",
            "DELETE FROM pais"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientEquipmentRelocationPersistenceTest {

    @Autowired private ClientEquipmentServicePort clientEquipmentServicePort;
    @Autowired private ServiceAreaServicePort serviceAreaServicePort;
    @Autowired private ManufacturerPersistenceAdapter manufacturerAdapter;
    @Autowired private BrandPersistenceAdapter brandAdapter;
    @Autowired private EquipmentTypePersistenceAdapter equipmentTypeAdapter;
    @Autowired private EquipmentPersistenceAdapter equipmentAdapter;
    @Autowired private ModelPersistenceAdapter modelAdapter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.valueOf(System.nanoTime());
    }

    private UUID unCliente() {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social)
                VALUES (?, ?, 'NIT_juridico', 'Hospital')
                """,
                cliente, String.valueOf(System.nanoTime() % 10_000_000_000L));

        return cliente;
    }

    /** Una sede nueva, con su propia ciudad y su propio pais, para el cliente dado. */
    private UUID unaSedeDe(UUID cliente) {
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        UUID sede = UUID.randomUUID();
        long n = System.nanoTime();
        String letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String iso = "" + letras.charAt((int) (n % 26))
                + letras.charAt((int) ((n / 26) % 26))
                + letras.charAt((int) ((n / 676) % 26));

        jdbcTemplate.update(
                "INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                pais, iso, "Pais " + n);
        jdbcTemplate.update(
                "INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Ciudad " + n, pais);
        jdbcTemplate.update(
                """
                INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero,
                                  k_id_cliente, k_id_ciudad)
                VALUES (?, ?, '10', '20', '30-40', ?, ?)
                """,
                sede, "Sede " + n, cliente, ciudad);

        return sede;
    }

    private UUID unModelo() {
        Manufacturer fabricante = manufacturerAdapter.save(Manufacturer.create("Draeger " + unico(), null));
        Brand marca = brandAdapter.save(Brand.create("Marca " + unico()));
        EquipmentType tipo = equipmentTypeAdapter.save(EquipmentType.create(
                "Tipo " + unico(), "Definicion", "Cuidados", "Electronica", null, null, null, 100_000L));
        Equipment asociacion = equipmentAdapter.save(Equipment.create(tipo.getId(), marca.getId()));
        Model modelo = modelAdapter.save(Model.create(null, fabricante.getId(), asociacion.getId()));

        return modelo.getId();
    }

    @Test
    @DisplayName("trasladar entre sedes del mismo cliente deja la unidad en el area nueva, en la fila real")
    void trasladaEntreSedesDelMismoClienteYPersiste() {
        UUID cliente = unCliente();
        UUID sedeNorte = unaSedeDe(cliente);
        UUID sedeSur = unaSedeDe(cliente);
        ServiceArea areaOrigen = serviceAreaServicePort.create(new CreateServiceAreaCommand("UCI", sedeNorte));
        ServiceArea areaDestino =
                serviceAreaServicePort.create(new CreateServiceAreaCommand("Urgencias", sedeSur));
        UUID modelo = unModelo();

        ClientEquipment unidad = clientEquipmentServicePort.register(new RegisterClientEquipmentCommand(
                "SN-" + unico(), modelo, areaOrigen.getId(), null, null, null));

        ClientEquipment trasladada = clientEquipmentServicePort.relocate(
                new RelocateClientEquipmentCommand(unidad.getId(), areaDestino.getId()));

        assertThat(trasladada.getIdAreaServicio()).isEqualTo(areaDestino.getId());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT k_id_area_servicio FROM equipo_cliente WHERE k_id_equipo_cliente = ?",
                        UUID.class, unidad.getId()))
                .isEqualTo(areaDestino.getId());
        assertThat(clientEquipmentServicePort.findById(unidad.getId()).getIdAreaServicio())
                .isEqualTo(areaDestino.getId());
    }
}
