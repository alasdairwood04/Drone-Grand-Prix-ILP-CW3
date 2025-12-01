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

        logger.info("Racer '{}' completed pathfinding. They have a physics time of {} the number of steps was {}.", profile.getName(), result != null ? result.totalTime() : "N/A", result != null ? result.path().size() : "N/A");

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
                .travelTime(physicsTime) // Add Time Element
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
        racers.add(RacerProfile.builder()
                .name("Geometric Drone (Naive)")
                .color("#0000FF") // Blue
                .strategy(SearchStrategy.ASTAR)
                .heuristicType(HeuristicType.EUCLIDEAN)
                .moveDistance(BASE_MOVE)       // Standard step size
                .flightAngles(FINE_STEERING) // Standard 16-point compass
                // --- PHYSICS DISABLED ---
                .baseSpeed(0.00015)          // Constant speed
                .dragFactor(0.0)             // 0.0 = No speed lost in corners
                .turningPenalty(0.0)         // 0.0 = No time penalty for turning
                .safetyMargin(0.00001)       // Very small margin (allows hugging the wall)
                .build());

        // DRONE B: The "Physics-Aware" Racer
        racers.add(RacerProfile.builder()
                .name("Physics Drone (Realistic)")
                .color("#FF0000") // Red
                .strategy(SearchStrategy.ASTAR)
                .heuristicType(HeuristicType.EUCLIDEAN)
                .moveDistance(BASE_MOVE)       // Same step size
                .flightAngles(FINE_STEERING)
                // ^ USE FINE STEERING (32 points) - Crucial for smooth curves!
                // --- PHYSICS ENABLED ---
                .baseSpeed(0.00030)          // Higher base speed makes momentum more valuable
                .dragFactor(0.015)           // HIGH DRAG: 0.015 * 90 deg = 1.35 (Speed crashes to min)
                .turningPenalty(1.5)         // Extra penalty for the "effort" of turning
                .safetyMargin(0.00001)       // Same margin (fair comparison)
                .build());

        // ==============  Experiment B: Contextual Adaptation (1. fast speed/slow in corners 2. average speed/excellent cornering) ==============


        // RACER 1: THE POWER HOUSE (Williams)
        // Strategy: Wins on straights, loses heavily in corners.
        // Must take VERY wide lines to avoid the high drag penalty.
//        racers.add(RacerProfile.builder()
//                .name("Williams (Power Spec)")
//                // red
//                .color("#ff1e41") // Red
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(COARSE_STEERING) // Use Fine Steering (32) if you have it, otherwise Coarse is ok
//                // --- PHYSICS CONFIG ---
//                .baseSpeed(0.00055)     // ~220 km/h (Very Fast on straights)
//                .dragFactor(0.025)       // HIGH DRAG: Turning 90 deg loses 180% speed (stops dead)
//                .turningPenalty(2)    // Unstable in corners
//                .safetyMargin(0.00001)
//                .build());
//
//        // RACER 2: THE TECHNICAL MASTER (Red Bull)
//        // Strategy: Slower on straights, but flows through corners effortlessly.
//        // Can take tighter lines because the drag penalty is negligible.
//        racers.add(RacerProfile.builder()
//                .name("Red Bull (Grip Spec)")
//                .color("#1E41FF") // Blue
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(COARSE_STEERING)
//                // --- PHYSICS CONFIG ---
//                .baseSpeed(0.00035)     // Moderate Speed (~140 km/h)
//                .dragFactor(0.002)      // LOW DRAG: Turning 90 deg loses only 18% speed
//                .turningPenalty(0.0)    // Agile (No time lost to stability)
//                .safetyMargin(0.00001)
//                .build());


        // ==============  Experiment C: Comparing veteran and noob drivers in same cars ==============


//        // RACER 2: THE ROOKIE (Scared)
//        // Strategy: Nervous. Stays well clear of any walls.
//        // Result: Forced to take a wider radius, increasing total path distance.
//        racers.add(RacerProfile.builder()
//                .name("The Rookie (Safe)")
//                .color("#FFA500") // Orange
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(COARSE_STEERING)
//                // --- PHYSICS CONFIG (Identical to Pro) ---
//                .baseSpeed(0.0004)
//                .dragFactor(0.01)
//                .turningPenalty(1.0)
//                // --- THE EXPERIMENT VARIABLE ---
//                .safetyMargin(0.0001)    // 11 Meters (Cautious)
//                .build());
//
//        // RACER 1: THE VETERAN (Pro)
//        // Strategy: Confident. Passes within inches of the barriers.
//        // Result: Takes the shortest possible physical line through the corner.
//        racers.add(RacerProfile.builder()
//                .name("The Veteran (Pro)")
//                .color("#00FF00") // Green
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(COARSE_STEERING) // Fine steering (32) is best if available
//                // --- PHYSICS CONFIG (Standard) ---
//                .baseSpeed(0.0004)       // Standard fast speed
//                .dragFactor(0.01)         // Standard drag
//                .turningPenalty(1.0)      // Standard penalty
//                // --- THE EXPERIMENT VARIABLE ---
//                .safetyMargin(0.00001)    // 1.1 Meters (Aggressive)
//                .build());


//        // simulate a car that is very fast on the straights but struggles in corners due to extra weight
//        racers.add(RacerProfile.builder()
//                .name("Williams - fast but heavy")
//                .color("#33FF57") // Spring Green
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(FINE_STEERING)
//                // PHYSICS CONFIG
//                .baseSpeed(0.0005)      // High Speed converts to 200 mph
//                .dragFactor(0.017)             // car loses 50% of speed in corners at 90 degrees
//                .safetyMargin(0.00001)  // Aggressive: Clips the walls - 1.1 meters away from walls
//                .turningPenalty(1.2)     // doing a hairpin (180 degrees) costs 1 second on top of normal drag
//                .build());
//
//        // simulate a car that has lots of downforce and grip but the added air resistance slows it down
//        racers.add(RacerProfile.builder()
//                .name("Redbull - slow but grippy")
//                .color("#FF5733") // Orange Red
//                .strategy(SearchStrategy.ASTAR)
//                .heuristicType(HeuristicType.EUCLIDEAN)
//                .moveDistance(BASE_MOVE)
//                .flightAngles(FINE_STEERING)
//                // PHYSICS CONFIG
//                .baseSpeed(0.00039)     // Moderate Speed converts to 180 mph
//                .dragFactor(0.003)            // car loses 30% of speed in corners at 90 degrees
//                .safetyMargin(0.00001) // Aggressive: Clips the walls
//                .turningPenalty(0.0)    // No turning penalty
//                .build());
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