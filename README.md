# Drone Grand Prix (ILP CW3)

This project is a Spring Boot application designed to manage drone race data, generate track geometry from images, and calculate flight paths. It includes a Python integration for processing visual track data.

## Prerequisites

* **Docker** (Recommended for running the application)
* **Java 21** & **Maven** (If running locally without Docker)

## How to Run (Using Docker)

The easiest way to run the application is using Docker, which automatically handles the Java and Python environment setup.

### 1. Build the Docker Image
Navigate to the project root (where the `Dockerfile` is located) and run:

```bash
docker build -t Drone-Grand-Prix-ILP-CW3 .
```

### 2. Run the Container
Start the application and map port 8080:

```bash
docker run -p 8080:8080 drone-grand-prix
```
The application will be available at: http://localhost:8080

## How to Run (Local Development)

## 1. Install Python Dependencies:

```bash
pip3 install numpy opencv-python-headless shapely pyproj
```

## 2. Run with Maven:
```bash
./mvnw spring-boot:run
```

## Features & Usage
- Web Interface: Access the dashboard at http://localhost:8080 to view the application UI.

- Track Generation: Upload an image of a hand-drawn track to convert it into GeoJSON geometry.

- API: The application exposes REST endpoints for drone command and control.

## Project Structure
- `src/main/java`: Spring Boot Java application source code.

- `src/main/resources/scripts`: Python scripts (track_processor.py) used for image processing.

- `src/main/resources/tracks`: Pre-loaded track definitions (GeoJSON).