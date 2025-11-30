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

        PathfindingService.PathResult result = pathfindingService.findPath(start, end, obstacles, profile);

        List<LngLat> path = (result != null) ? result.path() : new ArrayList<>();

        // use physics time from result if available
        double physicsTime = (result != null) ? result.totalTime() : 0.0;

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

        logger.info("Starting F1 Strategy Simulation from {} to {}", start, end);

        // 1. Load Track Boundaries (Obstacles)
        GeoJsonPolygon trackObstaclesGeo = request.getLlmInput();
        List<RestrictedArea> trackObstacles = trackGenerationService.convertFromGeoJson(trackObstaclesGeo);

        if (trackObstacles == null) {
            logger.info("No track input provided, generating procedural testing environment.");
        }

        // 2. Define Standard Physics Constants
        // 16-point compass = Standard steering rack
        double[] STANDARD_STEERING = {0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5, 180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5};

        // 32-point compass = Fine steering rack
        double[] FINE_STEERING = {0, 11.25, 22.5, 33.75, 45, 56.25, 67.5, 78.75, 90, 101.25, 112.5, 123.75, 135, 146.25,
                157.5, 168.75, 180, 191.25, 202.5, 213.75, 225, 236.25, 247.5, 258.75, 270, 281.25,
                292.5, 303.75, 315, 326.25, 337.5, 348.75};

        // 64-point compass = Coarse steering rack
        double[] COARSE_STEERING = {0, 5.625, 11.25, 16.875, 22.5, 28.125, 33.75, 39.375, 45, 50.625,
                56.25, 61.875, 67.5, 73.125, 78.75, 84.375, 90, 95.625, 101.25, 106.875,
                112.5, 118.125, 123.75, 129.375, 135, 140.625, 146.25, 151.875, 157.5,
                163.125, 168.75, 174.375, 180, 185.625, 191.25, 196.875, 202.5, 208.125,
                213.75, 219.375, 225, 230.625, 236.25, 241.875, 247.5, 253.125, 258.75,
                264.375, 270, 275.625, 281.25, 286.875, 292.5, 298.125, 303.75, 309.375,
                315, 320.625, 326.25, 331.875, 337.5, 343.125, 348.75, 354.375};


        // Base simulation tick distance (approx 15 meters)
        double BASE_MOVE = 0.00015;

        // smaller tick distance
        double FINE_MOVE = 0.0001;

        List<RacerProfile> racers = new ArrayList<>();

        // ==============  Experiment A: The Heuristic Battle ==============
//        racers.add(RacerProfile.builder()
//                .name("The Geometrician")
//                .color("#FF5733") // Orange Red
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(STANDARD_STEERING)
//                // PHYSICS CONFIG
//                .baseSpeed(0.00015) // Standard Speed
//                .dragFactor(0.0)              // No drag
//                .safetyMargin(0.00001)   // Aggressive: Clips the walls
//                .turningPenalty(0.0)     // No turning penalty
//                .build());
//
//
//
//        racers.add(RacerProfile.builder()
//                .name("The Smooth Operator")
//                .color("#33FF57") // Spring Green
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(STANDARD_STEERING)
//                // PHYSICS CONFIG
//                .baseSpeed(0.00015) // Standard Speed
//                .dragFactor(0.002)             // Moderate drag
//                .safetyMargin(0.00001)  // Aggressive: Clips the walls
//                .turningPenalty(10.0)    // Moderate turning penalty
//                .build());

        // ==============  Experiment B: Contextual Adaptation (1. fast speed/slow in corners 2. average speed/excellent cornering) ==============

        racers.add(RacerProfile.builder()
                .name("The Dragster (Williams)")
                .color("#33FF57") // Spring Green
                .strategy(SearchStrategy.ASTAR)
                .heuristicType(HeuristicType.EUCLIDEAN)
                .moveDistance(BASE_MOVE)
                .flightAngles(FINE_STEERING)
                // PHYSICS CONFIG
                .baseSpeed(0.0005)      // High Speed
                .dragFactor(0.001)             // High drag
                .safetyMargin(0.00001)  // Aggressive: Clips the walls
                .turningPenalty(5.0)     // Moderate turning penalty
                .build());

        racers.add(RacerProfile.builder()
                .name("The Areo-Car (RedBull)")
                .color("#FF5733") // Orange Red
                .strategy(SearchStrategy.ASTAR)
                .heuristicType(HeuristicType.EUCLIDEAN)
                .moveDistance(BASE_MOVE)
                .flightAngles(FINE_STEERING)
                // PHYSICS CONFIG
                .baseSpeed(0.000015)     // Moderate Speed
                .dragFactor(0.0005)            // Very Low drag
                .safetyMargin(0.00001) // Aggressive: Clips the walls
                .turningPenalty(0.0)    // No turning penalty
                .build());
//


//        racers.add(RacerProfile.builder()
//                .name("The Dragster (Williams)")
//                .color("#33FF57") // Spring Green
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(STANDARD_STEERING)
//                // PHYSICS CONFIG
//                .baseSpeed(0.00025)
//                .dragFactor(0.01)
//                .safetyMargin(0.00001)
//                .turningPenalty(5.0)
//                .build());



        // --- TEAM 1: RED BULL (High Downforce / Technical) ---
        // Strategy: Clips apexes (margin 0.0), loses very little speed in corners (drag low).
        // Result: Should dominate in twisty sectors.
//        racers.add(RacerProfile.builder()
//                .name("Williams (fast but unstable)")
//                .color("#1E41FF") // Racing Blue
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN) // Good for technical movement
//                .moveDistance(BASE_MOVE)
//                .flightAngles(STANDARD_STEERING)
//                // PHYSICS CONFIG
//                .baseSpeed(0.00025)       // Standard Speed
//                .dragFactor(0.01)       // Excellent cornering (low penalty)
//                .safetyMargin(0.00001)    // Aggressive: Clips the walls
//                .turningPenalty(5.0)      // Slight turning penalty to reflect downforce benefits
//                .build());
//
//        // --- TEAM 2: WILLIAMS (Low Drag / Monza Spec) ---
//        // Strategy: Fast on straights, but corners punish it heavily.
//        // Result: Will take wide, sweeping lines to avoid sharp turning angles.
//        racers.add(RacerProfile.builder()
//                .name("Redbull - areo car")
//                .color("#00A0DE") // Cyan
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(STANDARD_STEERING)
//                // PHYSICS CONFIG
//                .baseSpeed(0.00015)
//                .dragFactor(0.0005)
//                .safetyMargin(0.00001)
//                .turningPenalty(0.0)
//                .build());


        List<DroneRaceResult> results = new ArrayList<>();

        for (RacerProfile racer : racers) {
            logger.info("Simulating Race Strategy for: {}", racer.getName());
            // Note: runRacer method might need updating if you pass new profile parameters manually,
            // but since they are inside the 'profile' object, this call remains clean.
            DroneRaceResult result = runRacer(start, end, trackObstacles, racer);
            results.add(result);
        }

        // Convert Obstacles for Visualizer
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

        logger.info("Simulation complete. Generated Race ID: {}", response.getRaceId());
        return response;
    }
}