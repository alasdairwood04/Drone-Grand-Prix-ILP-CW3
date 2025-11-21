package uk.ac.ed.inf.ilpcw1.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GeoJsonPolygon {
    @JsonProperty("type")
    private final String type = "Polygon";

    // Polygons are nested: [ [ [lng,lat], [lng,lat]... ] ]
    @JsonProperty("coordinates")
    private List<List<List<Double>>> coordinates;
}