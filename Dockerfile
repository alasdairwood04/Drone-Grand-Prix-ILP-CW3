# ===============================
# Stage 1: Build the Application
# ===============================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom.xml and download dependencies first (caching step to speed up re-builds)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the full source code (includes src/main/resources/static for the frontend)
COPY src ./src

# Build the application (skipping tests to avoid environment issues during build)
RUN mvn clean package -DskipTests

# ===============================
# Stage 2: Run the Application
# ===============================
# Using a Debian-based image (Ubuntu) instead of Alpine for better Python compatibility
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install Python 3 and the specific libraries required by your scripts
# python3-opencv -> cv2
# python3-shapely -> shapely
# python3-pyproj -> pyproj
# python3-numpy -> numpy
# python-is-python3 -> ensures the command 'python' works for ProcessBuilder
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-numpy \
    python3-shapely \
    python3-pyproj \
    python3-opencv \
    python-is-python3 \
    && rm -rf /var/lib/apt/lists/*

# Copy the built JAR file from the builder stage
# Using *.jar handles the artifactId name change automatically
COPY --from=builder /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]