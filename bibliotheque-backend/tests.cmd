@echo off
rem Lance les tests backend dans l'image de build du projet (JDK 8), contre le
rem PostgreSQL de docker compose. Usage : tests.cmd [-Dtest=NomDeLaClasse]
cd /d "%~dp0"
docker compose -f ..\docker-compose.yml up -d db
docker run --rm --network bibiotheque_default -e POSTGRES_HOST=db -v "%cd%:/build" -v "%USERPROFILE%\.m2:/root/.m2" -w /build maven:3.8-eclipse-temurin-8 mvn -B test %*
