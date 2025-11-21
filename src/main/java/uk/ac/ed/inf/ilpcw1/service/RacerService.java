package uk.ac.ed.inf.ilpcw1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ed.inf.ilpcw1.data.*;
import uk.ac.ed.inf.ilpcw1.exception.DroneNotFoundException;
import uk.ac.ed.inf.ilpcw1.exception.InvalidRequestException;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RacerService {
    private static final Logger logger = LoggerFactory.getLogger(DroneQueryService.class);

    private final PathfindingService pathfindingService;
    private final TrackGenerationService trackGenerationService;


    @Autowired
    public RacerService(ILPServiceClient ilpServiceClient, PathfindingService pathfindingService,
                        RestService restService, TrackGenerationService trackGenerationService) {
        this.pathfindingService = new PathfindingService(new RestService());
        this.trackGenerationService = trackGenerationService;
    }

    public DroneRaceResult runRacer(LngLat start, LngLat end, List<RestrictedArea> obstacles,
                                          SearchStrategy strategy, String color) {
        long startTime = System.currentTimeMillis();
        List<LngLat> path = pathfindingService.findPath(start, end, obstacles, strategy);
        long duration = System.currentTimeMillis() - startTime;

        GeoJsonLineString geoJsonPath = convertToGeoJson(path);

        return DroneRaceResult.builder()
                .algorithmName(strategy.getPersonalityName())
                .moveCount(path != null ? path.size() : -1) // -1 indicates crash/no path
                .computationTimeMs(duration)
                .path(geoJsonPath)
                .color(color)
                .build();
    }

    public GeoJsonLineString convertToGeoJson(List<LngLat> path) {
        if (path == null) {
            return GeoJsonLineString.builder().coordinates(new ArrayList<>()).build();
        }

        List<List<Double>> coordinates = path.stream()
                .map(p -> List.of(p.getLongitude(), p.getLatitude()))
                .collect(Collectors.toList());

        return GeoJsonLineString.builder()
                .type("LineString")
                .coordinates(coordinates)
                .build();
    }

    public RaceDataResponse startRace(RaceDataRequest request) {
        LngLat start = request.getStartLocation();
        LngLat end = request.getEndLocation();

        logger.info("Starting race from {} to {}", start, end);

        // 1. Generate track (restricted areas)
        List<RestrictedArea> trackObstacles = trackGenerationService.generateTrack(start, end);
        logger.debug("Generated {} obstacles", trackObstacles == null ? 0 : trackObstacles.size());

        // 2. Run racers
        List<DroneRaceResult> results = new ArrayList<>();
        results.add(runRacer(start, end, trackObstacles, SearchStrategy.ASTAR, "#00FF00"));
        results.add(runRacer(start, end, trackObstacles, SearchStrategy.GREEDY, "#FF0000"));
        results.add(runRacer(start, end, trackObstacles, SearchStrategy.DIJKSTRA, "#0000FF"));

        // convert List<RestrictedArea> to List<LngLat>
        List<LngLat> obstacleCoordinates = trackObstacles.stream()
                .flatMap(area -> area.getVertices().stream())
                .collect(Collectors.toList());


        // 3. Convert obstacles to geojson for any client use (optional, not used in builder if types differ)
        GeoJsonLineString geoJsonObstacles = trackGenerationService.convertToGeoJson(obstacleCoordinates);
        logger.info("Converted obstacles to GeoJson with {} coordinates", geoJsonObstacles.getCoordinates().size());

        // 4. Build and return response. Pass the original RestrictedArea list to the builder
        //    to avoid the type-mismatch error observed when passing GeoJson list to a builder
        //    method expecting List<RestrictedArea>.
        RaceDataResponse response = RaceDataResponse.builder()
                .raceId(UUID.randomUUID().toString())
                .startLocation(start)
                .endLocation(end)
                .trackObstacles(trackObstacles)
                .droneResults(results)
                .build();

        logger.info("RaceDataResponse prepared with ID: {}", response.getRaceId());
        return response;
    }

}
