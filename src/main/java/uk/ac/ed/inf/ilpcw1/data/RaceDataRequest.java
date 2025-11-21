package uk.ac.ed.inf.ilpcw1.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RaceDataRequest {
    @JsonProperty("startLocation")
    private LngLat startLocation;

    @JsonProperty("endLocation")
    private LngLat endLocation;

    @JsonProperty("LLMInput")
    private List<RestrictedArea> llmInput;
}
