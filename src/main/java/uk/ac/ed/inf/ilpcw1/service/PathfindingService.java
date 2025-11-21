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

    // Default constants for backward compatibility or fallback
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
     * Creates a default "Standard Drone" profile using A* and standard movement.
     */
    public List<LngLat> findPath(LngLat start, LngLat goal, List<RestrictedArea> restrictedAreas) {
        // Create a default profile for legacy calls
        RacerProfile defaultProfile = RacerProfile.builder()
                .name("Standard Drone")
                .strategy(SearchStrategy.ASTAR)
                .moveDistance(DEFAULT_MOVE_DISTANCE)
                .flightAngles(DEFAULT_COMPASS_DIRECTIONS)
                .heuristicWeight(1.0)
                .build();

        return findPath(start, goal, restrictedAreas, defaultProfile);
    }

    /**
     * Find a path from start to goal avoiding restricted areas using a specific racer profile.
     *
     * @param start           Starting position
     * @param goal            Goal position
     * @param restrictedAreas Areas to avoid
     * @param profile         Racer configuration (strategy, speed, agility, weight)
     * @return List of positions forming the path (including start, excluding goal)
     */
    public List<LngLat> findPath(LngLat start, LngLat goal, List<RestrictedArea> restrictedAreas, RacerProfile profile) {
        logger.info("Finding path from {} to {} using profile: {}", start, goal, profile.getName());

        if (restService.isCloseTo(start, goal)) {
            return new ArrayList<>(List.of(start));
        }

        // Priority queue ordered by f-score (lowest score = highest priority)
        PriorityQueue<Node> priorityQueue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<String, Node> allNodes = new HashMap<>();
        Set<String> processed = new HashSet<>();

        // Initialize start node
        double startG = 0;
        // Heuristic depends on the specific drone's move distance (speed)
        double startH = heuristic(start, goal, profile);
        double startF = calculateScore(startG, startH, profile);

        Node startNode = new Node(start, null, startG, startH, startF);

        priorityQueue.add(startNode);
        allNodes.put(positionKey(start), startNode);

        int iterations = 0;
        // Standard limit, can be adjusted or moved to profile if needed
        final int MAX_ITERATIONS = 250000;

        List<Region> noFlyZones = restrictedAreas.stream()
                .map(this::convertToRegion)
                .toList();

        while (!priorityQueue.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = priorityQueue.poll();
            String currentKey = positionKey(current.position);

            // Goal Check
            if (restService.isCloseTo(current.position, goal)) {
                logger.info("{} found path in {} iterations with {} moves",
                        profile.getName(), iterations, current.g);
                return reconstructPath(current);
            }

            if (processed.contains(currentKey)) {
                continue;
            }
            processed.add(currentKey);

            // Explore Neighbors using DRONE-SPECIFIC flight angles
            for (double angle : profile.getFlightAngles()) {
                // Use DRONE-SPECIFIC move distance (speed)
                LngLat nextPos = restService.nextPosition(current.position, angle, profile.getMoveDistance());
                String nextKey = positionKey(nextPos);

                if (processed.contains(nextKey)) continue;

                // No-Fly Zone Check
                if (intersectsRestrictedArea(current.position, nextPos, noFlyZones)) continue;

                // Cost Calculation (1 move = cost 1, representing 1 'tick' of time)
                double tentativeG = current.g + 1;

                Node nextNode = allNodes.get(nextKey);

                // A* logic: If we found a cheaper path to this neighbor (or haven't seen it yet)
                if (nextNode == null || tentativeG < nextNode.g) {
                    double h = heuristic(nextPos, goal, profile);
                    double f = calculateScore(tentativeG, h, profile);

                    if (nextNode == null) {
                        nextNode = new Node(nextPos, current, tentativeG, h, f);
                        allNodes.put(nextKey, nextNode);
                    } else {
                        nextNode.parent = current;
                        nextNode.g = tentativeG;
                        nextNode.f = f; // Update priority
                    }

                    // Re-adding to queue ensures the node is re-evaluated with new priority
                    priorityQueue.add(nextNode);
                }
            }
        }

        logger.warn("{} failed to find path after {} iterations", profile.getName(), iterations);
        return null;
    }

    /**
     * Calculates the priority score (f) based on the racer profile strategy and weight.
     */
    private double calculateScore(double g, double h, RacerProfile profile) {
        return switch (profile.getStrategy()) {
            case ASTAR -> g + h;
            case GREEDY -> h; // Pure heuristic
            case DIJKSTRA -> g; // Only cost so far
            case WEIGHTED_ASTAR -> g + (h * profile.getHeuristicWeight()); // Weighted heuristic
            default -> g + h;
        };
    }

    /**
     * Estimates the minimum steps remaining to reach the goal.
     * Must account for the specific drone's speed (moveDistance).
     */
    private double heuristic(LngLat current, LngLat goal, RacerProfile profile) {
        double dx = Math.abs(current.getLongitude() - goal.getLongitude());
        double dy = Math.abs(current.getLatitude() - goal.getLatitude());
        double moveDist = profile.getMoveDistance();

        // We divide by moveDist to convert "degrees" into "steps" (approximate g-score units)
        return switch (profile.getHeuristicType()) {
            case MANHATTAN -> (dx + dy) / moveDist;

            case CHEBYSHEV -> Math.max(dx, dy) / moveDist;

            case EUCLIDEAN -> Math.sqrt(dx * dx + dy * dy) / moveDist;
        };
    }
    private String positionKey(LngLat pos) {
        return String.format("%.8f,%.8f", pos.getLongitude(), pos.getLatitude());
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

    private boolean intersectsRestrictedArea(LngLat pos1, LngLat pos2, List<Region> noFlyZones) {
        for (Region region : noFlyZones) {
            if (restService.isInRegion(pos1, region) || restService.isInRegion(pos2, region)) {
                return true;
            }
            List<LngLat> vertices = region.getVertices();
            for (int i = 0; i < vertices.size() - 1; i++) {
                if (lineSegmentsIntersect(pos1, pos2, vertices.get(i), vertices.get(i + 1))) {
                    return true;
                }
            }
        }
        return false;
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
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private double direction(LngLat p1, LngLat p2, LngLat p3) {
        return (p3.getLongitude() - p1.getLongitude()) * (p2.getLatitude() - p1.getLatitude()) -
                (p2.getLongitude() - p1.getLongitude()) * (p3.getLatitude() - p1.getLatitude());
    }
}