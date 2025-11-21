package uk.ac.ed.inf.ilpcw1.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GeoJsonPoint {
    @JsonProperty("type")
    private final String type = "Point";

    @JsonProperty("coordinates")
    private List<Double> coordinates; // [lng, lat]
}