# Etapa de compilacion: JDK completo + Maven.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Se copian primero el descriptor y el wrapper para aprovechar la cache de capas:
# las dependencias se resuelven una sola vez mientras el codigo cambie.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp clean package -DskipTests

# Etapa de ejecucion: solo el JRE y el jar.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario sin privilegios: el proceso no corre como root.
RUN addgroup -S devtrack && adduser -S -G devtrack devtrack

WORKDIR /app
COPY --from=build /workspace/target/devtrack-*.jar app.jar
RUN chown -R devtrack:devtrack /app

USER devtrack
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
