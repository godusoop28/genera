# --- Etapa de build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B clean package -DskipTests

# --- Etapa de ejecución ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# LibreOffice headless es opcional: solo se usa si PDF_CONVERSION_ENABLED=true
# (ver app.contracts.pdf-conversion-enabled, LibreOfficePdfConverter). El DOCX
# del contrato siempre se genera igual sin esto.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libreoffice-writer \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
