package com.malphasos.malphasos.location.infrastructure.input.mapper;

import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.country.Country;
import com.malphasos.malphasos.location.infrastructure.input.model.response.CityResponse;
import com.malphasos.malphasos.location.infrastructure.input.model.response.CountryResponse;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * Traduce los agregados a la respuesta del API.
 *
 * <p>Solo en esa dirección. Hacia dentro no hay mapeo automático: el controlador construye el
 * comando a mano porque el identificador viene de la ruta y no del cuerpo, y porque el comando lleva
 * exactamente los campos que la operación necesita, ni uno más. Un mapeo generado desde el DTO
 * arrastraría la forma del API hasta la capa de aplicación.
 */
@Mapper(componentModel = "spring")
public interface LocationRestMapper {

    CountryResponse toResponse(Country country);

    List<CountryResponse> toCountryResponseList(List<Country> countries);

    CityResponse toResponse(City city);

    List<CityResponse> toCityResponseList(List<City> cities);
}
