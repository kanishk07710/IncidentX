# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# --- Runtime stage ---
# Uses eclipse-temurin's JRE image and adds Node.js so SANDBOX_MODE=node works without a
# Docker daemon (Render's standard web service doesn't expose one to the app container).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Pre-install the sandbox's node_modules at image build time so "node" mode submissions never
# depend on a runtime npm install. Previously this happened lazily on the first submission
# after every container start, and on Render's free tier (container spins down after idle and
# has to cold-boot on the next request) that install raced the user's very first submission,
# surfacing as a sandbox error. Baking it into the image removes the runtime network dependency
# entirely — see SandboxService.ensureNodeModulesForNodeMode(), which links this in.
COPY src/main/resources/sandbox/package.json /opt/sandbox-deps/package.json
RUN cd /opt/sandbox-deps && npm install --omit=dev --no-audit --no-fund

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
