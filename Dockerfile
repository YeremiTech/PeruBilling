FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package

FROM eclipse-temurin:25-jre
ENV SPRING_PROFILES_ACTIVE=prod \
    PRODUCTION_GUARD_ENABLED=true
RUN useradd --system --uid 10001 perubilling
WORKDIR /app
COPY --from=build /workspace/target/perubilling-*.jar /app/perubilling.jar
RUN mkdir -p /data/artifacts /opt/perubilling/sunat-xsd && chown -R perubilling:perubilling /app /data /opt/perubilling
USER perubilling
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/urandom","-jar","/app/perubilling.jar"]
