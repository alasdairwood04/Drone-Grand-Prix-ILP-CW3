# ------------ Stage 1: Build ------------

# Use a Maven image that includes Java 21
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy only pom.xml first (for dependency caching)
COPY pom.xml .
# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Now copy the source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# ------------ Stage 2: Run ------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 1. Install Python3 and dependencies
# opencv-python-headless is crucial for servers (no GUI)
RUN apk add --no-cache python3 py3-pip && \
    pip3 install --break-system-packages numpy opencv-python-headless shapely

# 2. Copy the jar (Keep as is)
COPY --from=builder /app/target/ilp_submission_image-0.0.1-SNAPSHOT.jar app.jar

# 3. Copy your python script into the container
COPY src/main/resources/scripts/track_processor.py /app/scripts/track_processor.py

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]