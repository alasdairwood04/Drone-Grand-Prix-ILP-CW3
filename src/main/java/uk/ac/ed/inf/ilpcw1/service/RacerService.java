package uk.ac.ed.inf.ilpcw1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ed.inf.ilpcw1.data.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RacerService {
    private static final Logger logger = LoggerFactory.getLogger(RacerService.class);

    private final PathfindingService pathfindingService;
    private final TrackGenerationService trackGenerationService;

    @Autowired
    public RacerService(ILPServiceClient ilpServiceClient, PathfindingService pathfindingService,
                        RestService restService, TrackGenerationService trackGenerationService) {
        this.pathfindingService = pathfindingService;
        this.trackGenerationService = trackGenerationService;
    }

    public DroneRaceResult runRacer(LngLat start, LngLat end, List<RestrictedArea> obstacles, RacerProfile profile) {
        long startTime = System.currentTimeMillis();

        List<LngLat> path = pathfindingService.findPath(start, end, obstacles, profile);

        long duration = System.currentTimeMillis() - startTime;

        GeoJsonLineString geoJsonPath = convertToGeoJson(path);

        // Calculate travel time (in this simulation, 1 move = 1 tick/second)
        double travelTime = (path != null) ? (double) path.size() : 0.0;

        return DroneRaceResult.builder()
                .algorithmName(profile.getName())
                .moveCount(path != null ? path.size() : -1)
                .computationTimeMs(duration)
                .path(geoJsonPath)
                .color(profile.getColor())
                .travelTime(travelTime) // Add Time Element
                .build();
    }

    // ... convertToGeoJson ...
    public GeoJsonLineString convertToGeoJson(List<LngLat> path) {
        if (path == null) {
            return GeoJsonLineString.builder().coordinates(new ArrayList<>()).build();
        }
        List<List<Double>> coordinates = path.stream()
                .map(p -> List.of(p.getLongitude(), p.getLatitude()))
                .collect(Collectors.toList());
        return GeoJsonLineString.builder().type("LineString").coordinates(coordinates).build();
    }

    // ... startRace ...
    public RaceDataResponse startRace(RaceDataRequest request) {
        LngLat start = request.getStartLocation();
        LngLat end = request.getEndLocation();

        logger.info("Starting race from {} to {}", start, end);

        GeoJsonPolygon trackObstaclesGeo = request.getLlmInput();
        List<RestrictedArea> trackObstacles = trackGenerationService.convertFromGeoJson(trackObstaclesGeo);

        if (trackObstacles == null) {
            logger.info("No LLM input provided, generating track procedurally.");
        }

        logger.info("Using {} obstacles", trackObstacles.size());

        List<RacerProfile> racers = new ArrayList<>();

        // 1. Standard A*
        racers.add(RacerProfile.builder()
                .name("Optimal Ace")
                .color("#00FF00")
                .strategy(SearchStrategy.ASTAR)
                .moveDistance(0.00015)
                .flightAngles(new double[]{0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5, 180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5})
                .heuristicWeight(1.0)
                .build());

        // 2. Weighted A* (Faster)
        racers.add(RacerProfile.builder()
                .name("The Muscle")
                .color("#0000FF")
                .strategy(SearchStrategy.WEIGHTED_ASTAR)
                .moveDistance(0.00030)
                .flightAngles(new double[]{0, 45, 90, 135, 180, 225, 270, 315})
                .heuristicWeight(2.5)
                .build());

        // 3. Greedy
        racers.add(RacerProfile.builder()
                .name("Swift Seeker")
                .color("#FF0000")
                .strategy(SearchStrategy.GREEDY)
                .moveDistance(0.00020)
                .flightAngles(new double[]{0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330})
                .heuristicWeight(1.5)
                .build());

        // 4. Dijkstra
        racers.add(RacerProfile.builder()
                .name("Cautious Cruiser")
                .color("#00FFFF")
                .strategy(SearchStrategy.DIJKSTRA)
                .moveDistance(0.00015)
                .flightAngles(new double[]{0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5, 180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5})
                .heuristicWeight(0.0)
                .build());

        // 5. Manhattan
        racers.add(RacerProfile.builder()
                .name("The Taxi Driver")
                .color("#FFFF00")
                .strategy(SearchStrategy.ASTAR)
                .heuristicType(HeuristicType.MANHATTAN)
                .moveDistance(0.00015)
                .flightAngles(new double[]{0, 90, 180, 270})
                .build());

        // 6. Chebyshev
        racers.add(RacerProfile.builder()
                .name("The King")
                .color("#800080")
                .strategy(SearchStrategy.ASTAR)
                .heuristicType(HeuristicType.CHEBYSHEV)
                .moveDistance(0.00015)
                .flightAngles(new double[]{0, 45, 90, 135, 180, 225, 270, 315})
                .build());

        List<DroneRaceResult> results = new ArrayList<>();

        for (RacerProfile racer : racers) {
            logger.info("Racing Profile: {}", racer.getName());
            DroneRaceResult result = runRacer(start, end, trackObstacles, racer);
            results.add(result);
        }

        List<LngLat> obstacleCoordinates = trackObstacles.stream()
                .flatMap(area -> area.getVertices().stream())
                .collect(Collectors.toList());

        GeoJsonLineString geoJsonObstacles = trackGenerationService.convertToGeoJson(obstacleCoordinates);

        RaceDataResponse response = RaceDataResponse.builder()
                .raceId(UUID.randomUUID().toString())
                .startLocation(start)
                .endLocation(end)
                .trackObstacles(geoJsonObstacles)
                .droneResults(results)
                .build();

        logger.info("RaceDataResponse prepared with ID: {}", response.getRaceId());
        return response;
    }
}