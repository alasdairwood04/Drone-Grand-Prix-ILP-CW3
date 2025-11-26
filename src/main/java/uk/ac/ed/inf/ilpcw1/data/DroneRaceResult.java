package uk.ac.ed.inf.ilpcw1.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a GeoJSON LineString structure
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DroneRaceResult {
    @JsonProperty("algorithmName")
    private String algorithmName;   // e.g., "Optimal Ace (A*)"

    @JsonProperty("moveCount")
    private int moveCount;

    @JsonProperty("computationTimeMs")
    private long computationTimeMs;

    @JsonProperty("path")
    private GeoJsonLineString path;

    @JsonProperty("color")
    private String color; // Hex code for frontend visualization (e.g., "#FF0000")

    @JsonProperty("travelTime")
    private Double travelTime; // Simulation time (e.g. moves)
}
