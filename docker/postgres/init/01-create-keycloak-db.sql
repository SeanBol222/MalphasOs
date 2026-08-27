-- Crea la base de datos dedicada de Keycloak dentro de la misma instancia de
-- PostgreSQL que usa la aplicacion (patron heredado de bolivarbioingenieria-app:
-- una sola instancia, bases separadas).
--
-- Este script SOLO se ejecuta cuando el volumen de datos se crea por primera vez.
-- Por eso el esquema de la aplicacion NO vive aqui: lo gestiona Flyway desde el
-- backend, de forma versionada y reproducible en cualquier entorno.
--
-- El nombre viene de la variable de entorno KC_DB_NAME (ver .env.example).

\set kc_db_name `echo "${KC_DB_NAME:-keycloak}"`

SELECT format('CREATE DATABASE %I OWNER %I', :'kc_db_name', current_user)
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = :'kc_db_name'
)
\gexec
