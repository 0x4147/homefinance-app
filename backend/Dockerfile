# Build Stage
FROM maven:3.9.5-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Resolve dependencies first so they are cached as a layer until pom.xml changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/target/homefinance-backend-*.jar homefinance-backend.jar

USER app
EXPOSE 8585
ENTRYPOINT ["java", "-jar", "homefinance-backend.jar"]
