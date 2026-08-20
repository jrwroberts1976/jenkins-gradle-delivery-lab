FROM gradle:9.7.0-jdk21 AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon :app:installDist

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/app/build/install/app /app
EXPOSE 8080
USER 10001
ENTRYPOINT ["/app/bin/app"]
