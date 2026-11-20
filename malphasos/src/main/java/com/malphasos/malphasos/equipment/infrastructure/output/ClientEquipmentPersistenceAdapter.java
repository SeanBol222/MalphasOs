package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.infrastructure.output.mapper.EquipmentChainPersistenceMapper;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.ClientEquipmentRepository;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Implementa el almacen sobre JPA. */
@Component
@RequiredArgsConstructor
public class ClientEquipmentPersistenceAdapter implements ClientEquipmentPersistencePort {

    private final ClientEquipmentRepository clientEquipmentRepository;
    private final EquipmentChainPersistenceMapper mapper;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<ClientEquipment> findAll() {
        return mapper.toClientEquipmentList(clientEquipmentRepository.findAll());
    }

    @Override
    public Optional<ClientEquipment> findById(UUID id) {
        return clientEquipmentRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ClientEquipment> findByServiceArea(UUID idAreaServicio) {
        return mapper.toClientEquipmentList(clientEquipmentRepository.findByIdAreaServicio(idAreaServicio));
    }

    @Override
    public List<ClientEquipment> findByServiceAreaIn(Collection<UUID> idsAreas) {
        return mapper.toClientEquipmentList(clientEquipmentRepository.findByIdAreaServicioIn(idsAreas));
    }

    /** Con SQL y no con JPA: son tres saltos de la cadena y solo hace falta el último identificador. */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> findEquipmentTypes(Collection<UUID> idsEquipos) {
        Map<UUID, UUID> tipos = new HashMap<>();

        if (idsEquipos.isEmpty()) {
            return tipos;
        }

        jdbc.query(
                """
                SELECT ec.k_id_equipo_cliente, e.k_id_tipo_equipo
                FROM equipo_cliente ec
                JOIN modelo m ON m.k_id_modelo = ec.k_id_modelo
                JOIN equipo e ON e.k_id_equipo = m.k_id_equipo
                WHERE ec.k_id_equipo_cliente IN (:ids)
                """,
                Map.of("ids", idsEquipos),
                fila -> {
                    tipos.put(fila.getObject("k_id_equipo_cliente", UUID.class),
                            fila.getObject("k_id_tipo_equipo", UUID.class));
                });

        return tipos;
    }

    @Override
    public ClientEquipment save(ClientEquipment clientEquipment) {
        // saveAndFlush y no save: el numero de hoja de vida lo pone un trigger al insertar, y sin flush el
        // INSERT ocurriria al confirmar, despues de devolver una unidad sin numero.
        return mapper.toDomain(clientEquipmentRepository.saveAndFlush(mapper.toEntity(clientEquipment)));
    }
}
