package uk.ac.ed.inf.ilpcw1.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.inf.ilpcw1.data.*;
import uk.ac.ed.inf.ilpcw1.service.*;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/v1/race")
public class RaceController {


    private final GeoJsonMapper geoJsonMapper;
    private static final Logger logger = LoggerFactory.getLogger(RaceController.class);
    private final TrackGenerationService trackGenerationService;
    private final RacerService racerService;

    @Autowired
    public RaceController(
            TrackGenerationService trackGenerationService,
            RacerService racerService,
            GeoJsonMapper geoJsonMapper) {
        this.trackGenerationService = trackGenerationService;
        this.racerService = racerService;
        this.geoJsonMapper = geoJsonMapper;
    }

    @PostMapping("/start")
    public ResponseEntity<RaceDataResponse> startRace(@RequestBody RaceDataRequest request){
        RaceDataResponse response = racerService.startRace(request);
        logger.info("Returning race response with ID: {}", response.getRaceId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/visualize")
    public ResponseEntity<GeoJsonFeatureCollection> getRaceVisualization(@RequestBody RaceDataRequest request) {
        // 1. Run the race logic
        RaceDataResponse raceData = racerService.startRace(request);

        // 2. Convert to standardized GeoJSON FeatureCollection
        GeoJsonFeatureCollection geoJsonOutput = geoJsonMapper.convertRaceDataToGeoJson(raceData);

        return ResponseEntity.ok(geoJsonOutput);
    }
}