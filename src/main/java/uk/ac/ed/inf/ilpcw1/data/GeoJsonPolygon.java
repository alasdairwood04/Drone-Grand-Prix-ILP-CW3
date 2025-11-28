package uk.ac.ed.inf.ilpcw1.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeoJsonPolygon {

    @Builder.Default
    @JsonProperty("type")
    private String type = "Polygon";

    // Nested structure: [ [ [lng,lat], [lng,lat]... ] ]
    @JsonProperty("coordinates")
    private List<List<List<Double>>> coordinates;
}