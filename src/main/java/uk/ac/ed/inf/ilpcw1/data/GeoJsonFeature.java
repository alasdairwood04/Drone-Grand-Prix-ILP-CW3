package uk.ac.ed.inf.ilpcw1.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class GeoJsonFeature {
    @Builder.Default
    @JsonProperty("type")
    private String type = "Feature";

    // This holds GeoJsonLineString, GeoJsonPoint, or GeoJsonPolygon
    @JsonProperty("geometry")
    private Object geometry;

    // Holds metadata like color, algorithm name, move count (geojson.io reads these for styling)
    @JsonProperty("properties")
    private Map<String, Object> properties;
}