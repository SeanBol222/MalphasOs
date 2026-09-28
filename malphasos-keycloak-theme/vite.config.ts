import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { keycloakify } from "keycloakify/vite-plugin";

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        react(),
        keycloakify({
            // El nombre con el que el tema aparece en el desplegable de la consola de Keycloak, y el
            // que el realm versionado nombra en loginTheme y adminTheme.
            themeName: "malphasos",
            // La consola de cuenta del usuario se queda con la de Keycloak por ahora: esta tanda cubre
            // el login y la consola de administracion. Cambiar esto exige `npx keycloakify
            // initialize-account-theme`, no solo tocar este valor.
            accountThemeImplementation: "none"
        })
    ]
});
