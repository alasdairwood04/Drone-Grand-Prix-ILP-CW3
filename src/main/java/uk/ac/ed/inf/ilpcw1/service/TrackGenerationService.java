package uk.ac.ed.inf.ilpcw1.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.ed.inf.ilpcw1.data.GeoJsonLineString;
import uk.ac.ed.inf.ilpcw1.data.GeoJsonPolygon;
import uk.ac.ed.inf.ilpcw1.data.LngLat;
import uk.ac.ed.inf.ilpcw1.data.RestrictedArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrackGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(DroneQueryService.class);


    /**
     * Loads pre-defined tracks from the tracks.json resource file.
     * @return A Map where the key is the track name (e.g., "Austin") and the value is the GeoJsonPolygon.
     */
    public Map<String, GeoJsonPolygon> getPreloadedTracks() {
        Map<String, GeoJsonPolygon> tracks = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            // 1. Find all .json files in the tracks folder
            Resource[] resources = resolver.getResources("classpath:tracks/*.json");

            for (Resource resource : resources) {
                try {
                    // 2. Determine Track Name from Filename (e.g., "Austin.json" -> "Austin")
                    String filename = resource.getFilename();
                    if (filename == null) continue;

                    String trackName = filename.replace(".json", "");

                    // 3. Parse the file content into GeoJsonPolygon
                    GeoJsonPolygon trackPoly = mapper.readValue(resource.getInputStream(), GeoJsonPolygon.class);

                    logger.info("Parsed track file: {} as {}", filename, trackPoly);

                    // 4. Add to map
                    tracks.put(trackName, trackPoly);
                    logger.info("Loaded track: {}", trackName);

                } catch (IOException e) {
                    logger.error("Failed to load track file: " + resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            logger.error("Could not access tracks directory", e);
        }

        return tracks;
    }
    public GeoJsonLineString extractTrackFromImage(MultipartFile file) throws IOException {
        // 1. Create Temp Files for Image AND Script
        Path tempImageFile = Files.createTempFile("upload_", ".png");
        Path tempScriptFile = Files.createTempFile("script_", ".py");

        try {
            // Copy the uploaded image to temp file
            file.transferTo(tempImageFile.toFile());

            // Copy the Python script from resources to temp file
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

        return GeoJsonLineString.builder()
                .type("LineString")
                .coordinates(coordinates)
                .build();
    }

    public List<RestrictedArea> convertFromGeoJson(GeoJsonPolygon geoJson) {
        if (geoJson == null || geoJson.getCoordinates() == null || geoJson.getCoordinates().isEmpty()) {
            return new ArrayList<>();
        }

        List<LngLat> singlePathVertices = new ArrayList<>();

        // 1. Get Outer Ring (The main track boundary)
        List<List<Double>> outerRing = geoJson.getCoordinates().get(0);
        for (List<Double> coord : outerRing) {
            singlePathVertices.add(new LngLat(coord.get(0), coord.get(1)));
        }

        // 2. Handle Holes (The Infield)
        if (geoJson.getCoordinates().size() > 1) {
            // Iterate through all holes (usually just 1 for a race track)
            for (int i = 1; i < geoJson.getCoordinates().size(); i++) {
                List<List<Double>> holeRing = geoJson.getCoordinates().get(i);

                // Add the hole vertices to our single path
                for (List<Double> coord : holeRing) {
                    singlePathVertices.add(new LngLat(coord.get(0), coord.get(1)));
                }

                // Close the hole loop (go back to first point of hole)
                List<Double> firstHolePoint = holeRing.get(0);
                singlePathVertices.add(new LngLat(firstHolePoint.get(0), firstHolePoint.get(1)));

                // "Cut" back to the start of the Outer Ring to close the entire shape
                List<Double> startOuter = outerRing.get(0);
                singlePathVertices.add(new LngLat(startOuter.get(0), startOuter.get(1)));
            }
        }

        RestrictedArea trackBoundary = RestrictedArea.builder()
                .name("User Drawn Track")
                .vertices(singlePathVertices)
                .build();

        return List.of(trackBoundary);
    }}