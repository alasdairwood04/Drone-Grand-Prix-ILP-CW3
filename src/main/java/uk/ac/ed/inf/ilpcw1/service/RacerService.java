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

    /**
     * Runs a single racer based on the provided profile.
     */
    public DroneRaceResult runRacer(LngLat start, LngLat end, List<RestrictedArea> obstacles, RacerProfile profile) {
        long startTime = System.currentTimeMillis();

        // Use the overloaded findPath that accepts the RacerProfile
        List<LngLat> path = pathfindingService.findPath(start, end, obstacles, profile);

        long duration = System.currentTimeMillis() - startTime;

        GeoJsonLineString geoJsonPath = convertToGeoJson(path);

        return DroneRaceResult.builder()
                .algorithmName(profile.getName()) // Use the personality name (e.g., "The Muscle")
                .moveCount(path != null ? path.size() : -1) // -1 indicates crash/no path
                .computationTimeMs(duration)
                .path(geoJsonPath)
                .color(profile.getColor())
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

        // 1. Get track (restricted areas) from input or generator
        List<RestrictedArea> trackObstacles = request.getLlmInput();
        if (trackObstacles == null) {
            trackObstacles = new ArrayList<>();
        }
        logger.info("Using {} obstacles", trackObstacles.size());

        // 2. Define the Racers (Profiles)
        List<RacerProfile> racers = new ArrayList<>();

        // 1. The "Optimal Ace" (Standard A*)
        racers.add(RacerProfile.builder()
                .name("Optimal Ace")
                .color("#00FF00") // Green
                .strategy(SearchStrategy.ASTAR)
                .moveDistance(0.00015) // Standard precision
                .flightAngles(new double[]{0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5,
                        180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5}) // 16 angles
                .heuristicWeight(1.0)
                .build());

        // 2. "The Muscle" (Weighted A* + High Speed)
        racers.add(RacerProfile.builder()
                .name("The Muscle")
                .color("#0000FF") // Blue
                .strategy(SearchStrategy.WEIGHTED_ASTAR)
                .moveDistance(0.00030) // Double Speed! (Faster, but might miss narrow gaps)
                .flightAngles(new double[]{0, 45, 90, 135, 180, 225, 270, 315}) // Only 8 angles (Less agile)
                .heuristicWeight(2.5) // Weighted Heuristic for speed over optimality
                .build());

        // 3. "Swift Seeker" (Greedy)
        racers.add(RacerProfile.builder()
                .name("Swift Seeker")
                .color("#FF0000") // Red
                .strategy(SearchStrategy.GREEDY)
                .moveDistance(0.00020) // Moderate Speed
                .flightAngles(new double[]{0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330}) // 12 angles
                .heuristicWeight(1.5)
                .build());

        // 4. "Cautious Cruiser" (Dijkstra)
        racers.add(RacerProfile.builder()
                .name("Cautious Cruiser")
                .color("#00FFFF") // Cyan
                .strategy(SearchStrategy.DIJKSTRA)
                .moveDistance(0.03000) // Very Slow speed
                .flightAngles(new double[]{0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5,
                        180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5}) // 16 angles
                .heuristicWeight(0.0) // Dijkstra ignores heuristic
                .build());

        // Uses Manhattan distance. It might prefer staying on "grid lines" (cardinal directions)
        // creating a very robotic, zig-zagging flight path compared to the smooth Ace.
        racers.add(RacerProfile.builder()
                .name("The Taxi Driver")
                .color("#FFFF00") // Yellow
                .strategy(SearchStrategy.ASTAR)
                .heuristicType(HeuristicType.MANHATTAN) // <--- NEW
                .moveDistance(0.00015)
                .flightAngles(new double[]{0, 90, 180, 270}) // Only 4 directions!
                .build());

        // "The King"
        // Uses Chebyshev distance. It loves diagonals.
        racers.add(RacerProfile.builder()
                .name("The King")
                .color("#800080") // Purple
                .strategy(SearchStrategy.ASTAR)
                .heuristicType(HeuristicType.CHEBYSHEV) // <--- NEW
                .moveDistance(0.00015)
                .flightAngles(new double[]{0, 45, 90, 135, 180, 225, 270, 315}) // 8 directions
                .build());

        // 3. Execute the Race
        List<DroneRaceResult> results = new ArrayList<>();

        for (RacerProfile racer : racers) {
            logger.info("Racing Profile: {}", racer.getName());
            DroneRaceResult result = runRacer(start, end, trackObstacles, racer);
            results.add(result);
        }

        // 4. Prepare Visual Assets (Track Geometry)
        List<LngLat> obstacleCoordinates = trackObstacles.stream()
                .flatMap(area -> area.getVertices().stream())
                .collect(Collectors.toList());

        logger.info("Collected {} obstacle coordinates for GeoJson conversion", obstacleCoordinates.size());

        GeoJsonLineString geoJsonObstacles = trackGenerationService.convertToGeoJson(obstacleCoordinates);

        // 5. Build Final Response
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