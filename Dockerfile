FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# 1. Install Python and "python-is-python3" to ensure the 'python' command works
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      python3 python3-pip python3-dev \
      python-is-python3 \
      build-essential libproj-dev proj-bin \
      libgeos-dev libgl1-mesa-glx ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Install Python packages
RUN pip3 install --no-cache-dir \
      numpy \
      opencv-python-headless \
      shapely \
      pyproj \
    cv2 \


# 2. FIXED: Copy the correct jar name (matches artifactId in pom.xml)
COPY --from=builder /app/target/Drone-Grand-Prix-ILP-CW3-0.0.1-SNAPSHOT.jar app.jar

# Note: The Java code loads the script from the classpath (inside the JAR),
# so copying it to /app/scripts/ is optional, but good for debugging.
COPY src/main/resources/scripts/track_processor.py /app/scripts/track_processor.py

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]