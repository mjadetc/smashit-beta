# How Render builds and runs the backend. A Dockerfile is a recipe for a "container":
# a small Linux machine with exactly what the app needs. Render reads it top to bottom.
#
# Two stages: the first has Maven and the full JDK to BUILD the .jar; the second only
# has a Java runtime to RUN it. The final container is much smaller and has no build tools.

# ---------- stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml first and download the libraries. Docker caches each step, so as long as
# pom.xml doesn't change, the next build skips this slow step.
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Now the code. -DskipTests: the tests need a database, which doesn't exist during the
# build (run them on your computer before you push).
COPY src ./src
RUN mvn -q package -DskipTests

# ---------- stage 2: run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Only the finished .jar comes over from the build stage.
COPY --from=build /app/target/*.jar app.jar

# Render's free plan has 512 MB of memory: let Java use up to 75% of it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Documentation only: Render sets PORT itself, and application.properties reads it.
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]