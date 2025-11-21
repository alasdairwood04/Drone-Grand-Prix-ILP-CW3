package uk.ac.ed.inf.ilpcw1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ed.inf.ilpcw1.data.LngLat;
import uk.ac.ed.inf.ilpcw1.data.Region;
import uk.ac.ed.inf.ilpcw1.data.RestrictedArea;
import uk.ac.ed.inf.ilpcw1.data.SearchStrategy;

import java.util.*;

/**
 * Service for path finding operations with support for multiple search strategies.
 */
@Service
public class PathfindingService {
    private static final Logger logger = LoggerFactory.getLogger(PathfindingService.class);
    private static final double MOVE_DISTANCE = 0.00015;

    // 16 compass directions in degrees
    private static final double[] COMPASS_DIRECTIONS = {
            0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5,
            180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5
    };

    private final RestService restService;


    @Autowired
    public PathfindingService(RestService restService) {
        this.restService = restService;
    }

    /**
     * Overloaded method for backward compatibility (defaults to A*).
     */
    public List<LngLat> findPath(LngLat start, LngLat goal, List<RestrictedArea> restrictedAreas) {
        return findPath(start, goal, restrictedAreas, SearchStrategy.ASTAR);
    }

    /**
     * Find a path from start to goal avoiding restricted areas using a specific strategy.
     *
     * @param start Starting position
     * @param goal Goal position
     * @param restrictedAreas Areas to avoid
     * @param strategy The pathfinding algorithm/personality to use
     * @return List of positions forming the path (including start, excluding goal)
     */
    public List<LngLat> findPath(LngLat start, LngLat goal, List<RestrictedArea> restrictedAreas, SearchStrategy strategy) {
        logger.info("Finding path from {} to {} using strategy: {}", start, goal, strategy.getPersonalityName());

        if (restService.isCloseTo(start, goal)) {
            return new ArrayList<>(List.of(start));
        }

        // Priority queue ordered by f-score
        PriorityQueue<Node> priorityQueue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<String, Node> allNodes = new HashMap<>();
        Set<String> processed = new HashSet<>();

        // Initialize start node
        double startG = 0;
        double startH = heuristic(start, goal);
        double startF = calculateFScore(startG, startH, strategy);

        Node startNode = new Node(start, null, startG, startH, startF);

        priorityQueue.add(startNode);
        allNodes.put(positionKey(start), startNode);

        int iterations = 0;
        final int MAX_ITERATIONS = 75000;

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
                        strategy.getPersonalityName(), iterations, current.g);
                return reconstructPath(current);
            }

            if (processed.contains(currentKey)) {
                continue;
            }
            processed.add(currentKey);

            // Explore Neighbors
            for (double angle : COMPASS_DIRECTIONS) {
                LngLat nextPos = restService.nextPosition(current.position, angle);
                String nextKey = positionKey(nextPos);

                if (processed.contains(nextKey)) continue;

                // No-Fly Zone Check
                if (intersectsRestrictedArea(current.position, nextPos, noFlyZones)) continue;

                // Cost Calculation (1 move = cost 1)
                double tentativeG = current.g + 1;

                Node nextNode = allNodes.get(nextKey);

                // If we found a cheaper path to this neighbor (or haven't seen it yet)
                // Note: For Greedy, tentativeG doesn't affect sorting, but we track it for the path limit.
                if (nextNode == null || tentativeG < nextNode.g) {
                    double h = heuristic(nextPos, goal);
                    double f = calculateFScore(tentativeG, h, strategy);

                    if (nextNode == null) {
                        nextNode = new Node(nextPos, current, tentativeG, h, f);
                        allNodes.put(nextKey, nextNode);
                    } else {
                        nextNode.parent = current;
                        nextNode.g = tentativeG;
                        nextNode.f = f; // Update priority based on new cost/strategy
                    }

                    priorityQueue.add(nextNode);
                }
            }
        }

        logger.warn("{} failed to find path after {} iterations", strategy.getPersonalityName(), iterations);
        return null;
    }

    /**
     * Calculates the priority score (f) based on the selected strategy.
     */
    private double calculateFScore(double g, double h, SearchStrategy strategy) {
        return switch (strategy) {
            case ASTAR -> g + h;        // Standard A*
            case GREEDY -> h;           // Only care about distance to goal
            case DIJKSTRA -> g;         // Only care about cost so far
        };
    }

    private double heuristic(LngLat from, LngLat to) {
        return restService.calculateDistance(from, to) / MOVE_DISTANCE;
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