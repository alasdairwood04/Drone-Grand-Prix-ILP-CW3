package uk.ac.ed.inf.ilpcw1.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GeoJsonFeatureCollection {
    @Builder.Default
    @JsonProperty("type")
    private String type = "FeatureCollection";

    @JsonProperty("features")
    private List<GeoJsonFeature> features;
}