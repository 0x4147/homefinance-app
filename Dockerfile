# Build Stage
FROM maven:3.9.5-eclipse-temurin-17-alpine AS build
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=build /build/target/homefinance-backend-0.0.1-SNAPSHOT.jar homefinance-backend.jar
EXPOSE 8585 5005
ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "homefinance.jar"]
