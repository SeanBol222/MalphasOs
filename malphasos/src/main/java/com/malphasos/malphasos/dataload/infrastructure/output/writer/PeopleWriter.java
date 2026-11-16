package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static com.malphasos.malphasos.dataload.infrastructure.output.writer.Steps.paso;

import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import com.malphasos.malphasos.person.domain.person.PhonePerson;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Escribe las personas de un archivo como <b>fichas sin cuenta</b>: la cuenta se da después con «Dar
 * acceso», que crea el usuario y hace que la ficha tome su identificador. Una carga nunca lleva
 * contraseñas. El primer correo es el de la cuenta (V40), como en un alta.
 *
 * <p>Una persona que ya está —por su cédula— no se toca: la carga solo aprende su identificador.
 *
 * <p>La firma de la carga se guarda como la de una cuenta, y solo si la persona no tiene ya una: la que
 * alguien dibujó en MalphasOS manda sobre la que trae otro sistema.
 */
@Component
@RequiredArgsConstructor
class PeopleWriter {

    private final PersonPersistencePort personas;
    private final PersonSignaturePersistencePort firmas;

    void write(ReadFile archivo, LoadRefs refs) {
        Map<String, UUID> queEstan = personas.findAll().stream().filter(Person::isEstadoActivo)
                .collect(Collectors.toMap(p -> p.getCedula().trim(), Person::getIdentificador, (a, b) -> a));
        List<LoadDocument.Person> deLaCarga = archivo.documento().personas();

        for (int i = 0; i < deLaCarga.size(); i++) {
            LoadDocument.Person persona = deLaCarga.get(i);
            int n = i;
            UUID id = queEstan.computeIfAbsent(persona.cedula().trim(),
                    k -> paso(archivo.nombre(), "/personas/", n, () -> personas.save(ficha(persona)).getIdentificador()));
            refs.put(persona.ref(), id);
            if (persona.firma() != null && firmas.find(id).isEmpty()) {
                paso(archivo.nombre(), "/personas/" + n + "/firma", () -> firmas.save(id,
                        new PersonSignature(Base64.getDecoder().decode(persona.firma()), LocalDateTime.now())));
            }
        }
    }

    private static Person ficha(LoadDocument.Person persona) {
        List<EmailPerson> correos = persona.correos().stream()
                .map(correo -> EmailPerson.builder()
                        .idCorreoPersona(UUID.randomUUID())
                        .correoPersona(correo.trim())
                        .estadoActivo(true)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
        if (!correos.isEmpty()) {
            correos.getFirst().setPrincipal(true);
        }

        return Person.builder()
                .identificador(UUID.randomUUID())
                .cedula(persona.cedula().trim())
                .primerNombre(persona.primerNombre())
                .segundoNombre(persona.segundoNombre())
                .primerApellido(persona.primerApellido())
                .segundoApellido(persona.segundoApellido())
                .tipoPersona(PersonType.valueOf(persona.tipo()))
                .estadoActivo(true)
                .emailPersonList(correos)
                .phonePersonList(persona.telefonos().stream()
                        .map(telefono -> PhonePerson.builder()
                                .idTelefonoPersona(UUID.randomUUID())
                                .telefonoPersona(telefono.trim())
                                .estadoActivo(true)
                                .build())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();
    }
}
