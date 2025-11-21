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
public class RaceDataResponse {
    @JsonProperty("raceId")
    private String raceId;

    @JsonProperty("startLocation")
    private LngLat startLocation;

    @JsonProperty("endLocation")
    private LngLat endLocation;

    // The LLM-generated track (obstacles)
    @JsonProperty("trackObstacles")
    private GeoJsonLineString trackObstacles;

    // The results of the drones racing on this track
    @JsonProperty("droneResults")
    private List<DroneRaceResult> droneResults;
}
