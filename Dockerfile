# Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# Install Python runtime, system libs for pyproj/shapely/opencv and build tools
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      python3 python3-pip python3-dev \
      build-essential libproj-dev proj-bin \
      libgeos-dev libgl1-mesa-glx ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Install Python packages: provides cv2 (opencv-python-headless), numpy, shapely, pyproj
RUN pip3 install --no-cache-dir \
      numpy \
      opencv-python-headless \
      shapely \
      pyproj

# Copy jar and scripts
COPY --from=builder /app/target/ilp_submission_image-0.0.1-SNAPSHOT.jar app.jar
COPY src/main/resources/scripts/track_processor.py /app/scripts/track_processor.py

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
