FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp verify
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir /app/data && chown 10001:10001 /app/data
COPY --from=build /build/target/cezmec-0.1.0.jar /app/cezmec.jar
USER 10001:10001
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/cezmec.jar"]
