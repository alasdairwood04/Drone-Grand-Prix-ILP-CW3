package uk.ac.ed.inf.ilpcw1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ed.inf.ilpcw1.data.*;

import java.util.*;

/**
 * Service for path finding operations with support for multiple search strategies
 * and variable drone profiles (speed, agility, etc.).
 */
@Service
public class PathfindingService {
    private static final Logger logger = LoggerFactory.getLogger(PathfindingService.class);

    // Default constants for backward compatibility
    private static final double DEFAULT_MOVE_DISTANCE = 0.00015;
    private static final double[] DEFAULT_COMPASS_DIRECTIONS = {
            0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5,
            180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5
    };

    private final RestService restService;

    @Autowired
    public PathfindingService(RestService restService) {
        this.restService = restService;
    }

    /**
     * Backward compatibility method.
     */
    public List<LngLat> findPath(LngLat start, LngLat goal, List<RestrictedArea> allowedAreas) {
        RacerProfile defaultProfile = RacerProfile.builder()
                .name("Standard Drone")
                .strategy(SearchStrategy.ASTAR)
                .moveDistance(DEFAULT_MOVE_DISTANCE)
                .flightAngles(DEFAULT_COMPASS_DIRECTIONS)
                .heuristicWeight(1.0)
                .heuristicType(HeuristicType.EUCLIDEAN)
                .build();

        return findPath(start, goal, allowedAreas, defaultProfile);
    }

    /**
     * Find a path from start to goal ensuring the drone stays WITHIN the allowed areas.
     * Updated to use CoordinateKey and optimized loop from CW2.
     *
     * @param start           Starting position
     * @param goal            Goal position
     * @param allowedAreas    Areas the drone MUST stay inside (Boundaries)
     * @param profile         Racer configuration (strategy, speed, agility, weight)
     * @return List of positions forming the path (including start, excluding goal)
     */
    public List<LngLat> findPath(LngLat start, LngLat goal, List<RestrictedArea> allowedAreas, RacerProfile profile) {
        logger.info("Finding path from {} to {} using profile: {}", start, goal, profile.getName());

        if (restService.isCloseTo(start, goal)) {
            return new ArrayList<>(List.of(start));
        }

        // Priority queue ordered by f-score
        PriorityQueue<Node> priorityQueue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        // Use CoordinateKey for efficient and precise position lookups
        Map<CoordinateKey, Node> allNodes = new HashMap<>();

        // Initialize start node
        double startG = 0;
        double startH = heuristic(start, goal, profile);
        double startF = calculateScore(startG, startH, profile);

        Node startNode = new Node(start, null, startG, startH, startF);
        CoordinateKey startKey = CoordinateKey.fromLngLat(start);

        priorityQueue.add(startNode);
        allNodes.put(startKey, startNode);

        int iterations = 0;
        final int MAX_ITERATIONS = 150000;

        // Convert RestrictedAreas to Regions once for efficiency
        List<Region> boundaryRegions = allowedAreas.stream()
                .map(this::convertToRegion)
                .toList();

        while (!priorityQueue.isEmpty()) {
            iterations++;
            if (iterations > MAX_ITERATIONS) {
                logger.warn("{} failed to find path after {} iterations (Max Reached)", profile.getName(), iterations);
                return null;
            }

            Node current = priorityQueue.poll();
            CoordinateKey currentKey = CoordinateKey.fromLngLat(current.position);

            // Lazy Deletion: If we found a better path to this node while it was waiting, skip it.
            Node bestKnown = allNodes.get(currentKey);
            if (bestKnown != null && bestKnown.g < current.g) {
                continue;
            }

            // Goal Check
            if (restService.isCloseTo(current.position, goal)) {
                logger.info("{} found path in {} iterations with {} moves",
                        profile.getName(), iterations, current.g);
                return reconstructPath(current);
            }

            // Explore Neighbors using DRONE-SPECIFIC flight angles
            for (double angle : profile.getFlightAngles()) {
                // Use DRONE-SPECIFIC move distance (speed)
                LngLat nextPos = restService.nextPosition(current.position, angle, profile.getMoveDistance());
                CoordinateKey nextKey = CoordinateKey.fromLngLat(nextPos);

                // BOUNDARY CHECK: Ensure we DO NOT leave the allowed region
                if (leavesAllowedRegions(current.position, nextPos, boundaryRegions)) {
                    continue;
                }

                // Cost Calculation (1 move = cost 1)
                double tentativeG = current.g + 1;
                Node existingNode = allNodes.get(nextKey);

                // If we found a cheaper path to this neighbor (or haven't seen it yet)
                if (existingNode == null || tentativeG < existingNode.g) {
                    double h = heuristic(nextPos, goal, profile);
                    double f = calculateScore(tentativeG, h, profile);

                    // Create NEW node to avoid corrupting the PriorityQueue
                    Node newNode = new Node(nextPos, current, tentativeG, h, f);

                    allNodes.put(nextKey, newNode);
                    priorityQueue.add(newNode);
                }
            }
        }

        logger.warn("{} failed to find path - Priority Queue exhausted after {} iterations", profile.getName(), iterations);
        return null;
    }

    /**
     * Checks if the move from pos1 to pos2 exits any of the allowed regions.
     * Returns TRUE if the move is INVALID (i.e., it leaves the region).
     */
    private boolean leavesAllowedRegions(LngLat pos1, LngLat pos2, List<Region> allowedRegions) {
        // Quick check: if no allowed regions, any move is invalid
        if (allowedRegions.isEmpty()) return false;

        for (Region region : allowedRegions) {
            // Check if points are spatially outside the region
            if (restService.isOutsideRegion(pos1, region) || restService.isOutsideRegion(pos2, region)) {
                return true; // We are outside the allowed boundary
            }

            // Check if the path line crosses a boundary edge (prevents skipping over corners)
            List<LngLat> vertices = region.getVertices();
            for (int i = 0; i < vertices.size() - 1; i++) {
                if (lineSegmentsIntersect(pos1, pos2, vertices.get(i), vertices.get(i + 1))) {
                    return true; // Crossed a boundary line
                }
            }
            // Check closing edge
            if (!vertices.isEmpty() && lineSegmentsIntersect(pos1, pos2, vertices.get(vertices.size() - 1), vertices.get(0))) {
                return true; // Crossed the closing boundary line
            }
        }
        return false;
    }

    private double calculateScore(double g, double h, RacerProfile profile) {
        return switch (profile.getStrategy()) {
            case ASTAR -> g + h;
            case GREEDY -> h;
            case DIJKSTRA -> g;
            case WEIGHTED_ASTAR -> g + (h * profile.getHeuristicWeight());
            default -> g + h;
        };
    }

    private double heuristic(LngLat current, LngLat goal, RacerProfile profile) {
        double dx = Math.abs(current.getLongitude() - goal.getLongitude());
        double dy = Math.abs(current.getLatitude() - goal.getLatitude());
        double moveDist = profile.getMoveDistance();

        return switch (profile.getHeuristicType()) {
            case MANHATTAN -> (dx + dy) / moveDist;
            case CHEBYSHEV -> Math.max(dx, dy) / moveDist;
            case EUCLIDEAN -> restService.calculateDistance(current, goal) / moveDist;
        };
    }

    private List<LngLat> reconstructPath(Node goalNode) {
        List<LngLat> path = new ArrayList<>();
        Node current = goalNode;
        while (current != null) {
            path.add(current.position);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private Region convertToRegion(RestrictedArea area) {
        return Region.builder()
                .name(area.getName())
                .vertices(area.getVertices())
                .build();
    }

    private boolean lineSegmentsIntersect(LngLat p1, LngLat p2, LngLat p3, LngLat p4) {
        double d1 = direction(p3, p4, p1);
        double d2 = direction(p3, p4, p2);
        double d3 = direction(p1, p2, p3);
        double d4 = direction(p1, p2, p4);

        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private double direction(LngLat p1, LngLat p2, LngLat p3) {
        return (p3.getLongitude() - p1.getLongitude()) * (p2.getLatitude() - p1.getLatitude()) -
                (p2.getLongitude() - p1.getLongitude()) * (p3.getLatitude() - p1.getLatitude());
    }
}