package uk.ac.ed.inf.ilpcw1.service;

import org.springframework.stereotype.Service;
import uk.ac.ed.inf.ilpcw1.data.GeoJsonLineString;
import uk.ac.ed.inf.ilpcw1.data.LngLat;
import uk.ac.ed.inf.ilpcw1.data.RestrictedArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrackGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(DroneQueryService.class);

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

}