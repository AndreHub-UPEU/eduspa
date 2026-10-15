-- Para entornos sin Docker Compose: crea las 3 bases y el usuario.
-- (Con Docker, cada compose-dev.yml crea su base vía POSTGRES_DB.)
CREATE USER eduspace WITH PASSWORD 'eduspace';
CREATE DATABASE planificacion_db OWNER eduspace;
CREATE DATABASE alumnos_db OWNER eduspace;
CREATE DATABASE notificaciones_db OWNER eduspace;
