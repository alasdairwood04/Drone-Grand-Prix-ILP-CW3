package uk.ac.ed.inf.ilpcw1.service;

import org.springframework.stereotype.Service;
import uk.ac.ed.inf.ilpcw1.data.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeoJsonMapper {

    public GeoJsonFeatureCollection convertRaceDataToGeoJson(RaceDataResponse raceData) {
        List<GeoJsonFeature> features = new ArrayList<>();

        // 1. Add Start Location (Green Marker)
        features.add(createPointFeature(
                raceData.getStartLocation(),
                "Start Location",
                "#00FF00",
                "rocket"));

        // 2. Add End Location (Red Marker)
        features.add(createPointFeature(
                raceData.getEndLocation(),
                "End Location",
                "#FF0000",
                "star"));

        // 3. Add Track Obstacles (Grey Polygon/LineString)
        if (raceData.getTrackObstacles() != null) {
            // Assuming trackObstacles is currently a GeoJsonLineString in your DTO
            // We convert it to a Feature
            features.add(GeoJsonFeature.builder()
                    .geometry(raceData.getTrackObstacles())
                    .properties(Map.of(
                            "name", "Obstacle",
                            "stroke", "#555555",
                            "stroke-width", 2,
                            "stroke-opacity", 1,
                            "fill", "#555555",
                            "fill-opacity", 0.3
                    ))
                    .build());
        }

        // 4. Add Drone Paths (Colored Lines)
        if (raceData.getDroneResults() != null) {
            for (DroneRaceResult result : raceData.getDroneResults()) {
                Map<String, Object> props = new HashMap<>();
                props.put("algorithm", result.getAlgorithmName());
                props.put("moveCount", result.getMoveCount());
                props.put("timeMs", result.getComputationTimeMs());

                // geojson.io styling properties
                props.put("stroke", result.getColor());
                props.put("stroke-width", 4);
                props.put("stroke-opacity", 0.8);

                features.add(GeoJsonFeature.builder()
                        .geometry(result.getPath())
                        .properties(props)
                        .build());
            }
        }

        return GeoJsonFeatureCollection.builder()
                .features(features)
                .build();
    }

    private GeoJsonFeature createPointFeature(LngLat loc, String name, String color, String symbol) {
        if (loc == null) return null;

        return GeoJsonFeature.builder()
                .geometry(new GeoJsonPoint(List.of(loc.getLongitude(), loc.getLatitude())))
                .properties(Map.of(
                        "name", name,
                        "marker-color", color,
                        "marker-size", "medium",
                        "marker-symbol", symbol
                ))
                .build();
    }
}