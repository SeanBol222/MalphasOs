import { LADO_MAYOR, medidasReducidas } from './reducir-foto';

/**
 * Las medidas de la foto reducida (2026-11-08). La reducción en sí necesita un lienzo, que el entorno de
 * pruebas no tiene; lo que se fija aquí es la cuenta, que es donde una foto saldría deformada o agrandada.
 */
describe('Las medidas de la foto reducida', () => {
  it('el lado mayor queda en 400 y el otro en proporción, en horizontal y en vertical', () => {
    expect(LADO_MAYOR).toBe(400);
    expect(medidasReducidas(4000, 3000)).toEqual({ ancho: 400, alto: 300 });
    expect(medidasReducidas(3000, 4000)).toEqual({ ancho: 300, alto: 400 });
  });

  it('una foto ya pequeña no se agranda', () => {
    expect(medidasReducidas(320, 240)).toEqual({ ancho: 320, alto: 240 });
  });

  it('una muy alargada no deja un lado en cero', () => {
    expect(medidasReducidas(10000, 5)).toEqual({ ancho: 400, alto: 1 });
  });

  it('con otro lado mayor, el que se pida', () => {
    expect(medidasReducidas(4000, 2000, 300)).toEqual({ ancho: 300, alto: 150 });
  });
});
