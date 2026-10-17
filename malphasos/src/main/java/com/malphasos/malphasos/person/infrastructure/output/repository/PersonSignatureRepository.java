package com.malphasos.malphasos.person.infrastructure.output.repository;

import com.malphasos.malphasos.person.infrastructure.output.entities.PersonSignatureEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonSignatureRepository extends JpaRepository<PersonSignatureEntity, UUID> {
}
