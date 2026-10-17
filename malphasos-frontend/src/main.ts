import { bootstrapApplication } from '@angular/platform-browser';
import { cargarConfiguracion } from './app/core/configuracion/configuracion';

// Primero la configuración y después el resto, que se importa cuando ya está puesta: la condición del
// token y la configuración de Keycloak leen la dirección del API y la de Keycloak al importarse
// (2026-10-17).
cargarConfiguracion()
  .then(async () => {
    const [{ appConfig }, { App }] = await Promise.all([
      import('./app/app.config'),
      import('./app/app'),
    ]);

    await bootstrapApplication(App, appConfig);
  })
  .catch((err) => {
    console.error(err);
    // Sin esto, una configuración rota es una página en blanco.
    document.body.textContent =
      'MalphasOS no pudo arrancar: falta su configuración o está incompleta. Avise a quien la administra.';
  });
