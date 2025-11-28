package uk.ac.ed.inf.ilpcw1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.ed.inf.ilpcw1.data.GeoJsonLineString;
import uk.ac.ed.inf.ilpcw1.data.LngLat;
import uk.ac.ed.inf.ilpcw1.data.RestrictedArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrackGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(DroneQueryService.class);

    public GeoJsonLineString extractTrackFromImage(MultipartFile file) throws IOException {
        // 1. Create Temp Files for Image AND Script
        Path tempImageFile = Files.createTempFile("upload_", ".png");
        Path tempScriptFile = Files.createTempFile("script_", ".py");

        try {
            // Copy the uploaded image to temp file
            file.transferTo(tempImageFile.toFile());

            // COPY THE PYTHON SCRIPT FROM RESOURCES TO TEMP FILE
            ClassPathResource scriptResource = new ClassPathResource("scripts/track_processor.py");
            if (!scriptResource.exists()) {
                throw new RuntimeException("Python script not found in resources");
            }
            Files.copy(scriptResource.getInputStream(), tempScriptFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 2. Build the command using the temp script path
            ProcessBuilder pb = new ProcessBuilder(
                    "python", // or "python3"
                    tempScriptFile.toString(),
                    tempImageFile.toString()
            );

            // MERGE ERROR STREAM so you see Python errors
            pb.redirectErrorStream(true);

            // 3. Start the process
            Process process = pb.start();

            // 4. Read the Output
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                // Log the actual Python error
                throw new RuntimeException("Python script failed with code " + exitCode + ". Output: " + output);
            }

            // 5. Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(output, GeoJsonLineString.class);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process track: " + e.getMessage());
        } finally {
            // Clean up both temp files
            Files.deleteIfExists(tempImageFile);
            Files.deleteIfExists(tempScriptFile);
        }
    }

    /**
     * Generates a "Track" (list of No-Fly Zones) based on start/end points.
     * TODO: CW3 - Replace the logic below with an LLM call.
     * * Prompt Strategy: "Given start [lat,lng] and end [lat,lng], generate 3 rectangular
     * RestrictedAreas that intersect the direct line path, forcing a deviation."
     */
    public List<RestrictedArea> generateTrack(LngLat start, LngLat end) {
        // Placeholder: procedural generation
        return generateProceduralObstacles(start, end);
    }

    private List<RestrictedArea> generateProceduralObstacles(LngLat start, LngLat end) {
        // Calculate midpoint
        double midLng = (start.getLongitude() + end.getLongitude()) / 2;
        double midLat = (start.getLatitude() + end.getLatitude()) / 2;

        // Create a simple "Wall" obstacle in the middle to force pathfinding
        // Width 0.0005, Height 0.002 (Arbitrary size for testing)
        List<LngLat> wallVertices = List.of(
                new LngLat(midLng - 0.0005, midLat - 0.001),
                new LngLat(midLng + 0.0005, midLat - 0.001),
                new LngLat(midLng + 0.0005, midLat + 0.001),
                new LngLat(midLng - 0.0005, midLat + 0.001),
                new LngLat(midLng - 0.0005, midLat - 0.001) // Close polygon
        );

        RestrictedArea wall = RestrictedArea.builder()
                .name("Procedural Wall Obstacle")
                .id(123)
                .vertices(wallVertices)
                .build();

        return List.of(wall);
    }

    public GeoJsonLineString convertToGeoJson(List<LngLat> vertices) {
        logger.info("vertices: {}", vertices);

        List<List<Double>> coordinates = vertices.stream()
                .map(p -> List.of(p.getLongitude(), p.getLatitude()))
                .collect(Collectors.toList());

        // GeoJSON polygons usually require the type "Polygon", but if your UI expects "LineString"
        // (which essentially draws the outline), we can keep it as LineString.
        // If you want a filled shape, this should technically be "Polygon" and nested one level deeper [[...]].
        // Keeping as LineString for consistency with your existing GeoJsonLineString class.
        return GeoJsonLineString.builder()
                .type("LineString")
                .coordinates(coordinates)
                .build();
    }

    public List<RestrictedArea> convertFromGeoJson(GeoJsonLineString geoJson) {
        if (geoJson == null || geoJson.getCoordinates() == null) return new ArrayList<>();

        List<LngLat> vertices = geoJson.getCoordinates().stream()
                .map(coord -> new LngLat(coord.get(0), coord.get(1)))
                .collect(Collectors.toList());

        // The user drawing is the "Track" (The allowed area)
        RestrictedArea trackBoundary = RestrictedArea.builder()
                .name("User Drawn Track")
                .vertices(vertices)
                .build();

        return List.of(trackBoundary);
    }
}