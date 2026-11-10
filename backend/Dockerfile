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
# Memoria de la JVM. Historia: la instancia empezó con 512 MB; desde 2026-11 tiene 2 GB (cgroupMaxMb en
# /actuator/info). Revisa ese valor antes de tocar estos números: si se vuelve a 512 MB, regresa
# y ReservedCodeCacheSize=48m.
# MaxRAMPercentage=40: ~820 MB de heap con 2 GB (~205 MB con 512 MB). Fuera del heap el proceso usa ~250-300 MB
# (metaspace, code cache, hilos, nativa); un heap más grande podía pasar el límite y Render lo mataba (502).
# SerialGC y pilas de 512k: menos memoria fuera del heap; con 1 CPU un GC paralelo no ayuda.
# Compilador C2 activo otra vez (con 512 MB se apagó con TieredStopAtLevel=1 porque usaba decenas de MB al
# compilar): con 2 GB sobra y el código caliente (renderizar PDFs, imágenes, JSON) corre bastante más rápido.
# Code cache de 128m para C1+C2: si se llena, la JVM deja de compilar y todo se vuelve lento sin dar error.
# Min/MaxHeapFreeRatio: tras la recolección completa que hace MemoryRelief al terminar cada trabajo pesado, el
# heap se encoge y le regresa al sistema lo que no usa.
# ThreadPriorityPolicy=1 + JavaPriority1_To_OSPriority=10: los hilos de trabajos de fondo (prioridad mínima, ver
# CoreConfig) corren con nice 10 y Linux le da la CPU primero a las peticiones de las pantallas.
# MaxMetaspaceSize=256m: con 160m el metaspace se llenó tras 4 días (10/11/2026, OutOfMemoryError: Metaspace) y la
# JVM siguió viva pero fallando cada petición. El tope solo limita: no reserva memoria si no se usa.
# No usamos ExitOnOutOfMemoryError: los OOM de heap de un PDF grande se atrapan a propósito (falla solo ese
# documento). Para el metaspace, MetaspaceGuard avisa en el log al 80% y termina el proceso al 95% (Render lo
# reinicia solo). Diagnóstico: /actuator/info (container) y GET /api/v1/internal/diagnostics/jvm.
# glibc crea un arena de malloc por hilo y infla la memoria nativa del JVM en contenedores chicos.
ENV MALLOC_ARENA_MAX=2
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=128m -XX:MaxDirectMemorySize=48m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:ThreadPriorityPolicy=1 -XX:JavaPriority1_To_OSPriority=10"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
