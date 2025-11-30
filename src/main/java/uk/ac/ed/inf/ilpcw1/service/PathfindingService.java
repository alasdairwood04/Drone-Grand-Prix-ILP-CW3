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

    public record PathResult(List<LngLat> path, double totalTime) {}

    private final RestService restService;

    @Autowired
    public PathfindingService(RestService restService) {
        this.restService = restService;
    }

    /**
     * Backward compatibility method.
     */
    public PathResult findPath(LngLat start, LngLat goal, List<RestrictedArea> allowedAreas) {
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
    public PathResult findPath(LngLat start, LngLat goal, List<RestrictedArea> allowedAreas, RacerProfile profile) {
        logger.info("Finding path from {} to {} using profile: {}", start, goal, profile.getName());

        if (restService.isCloseTo(start, goal)) {
            return new PathResult(Collections.emptyList(), 0.0);
        }

        // Priority queue ordered by f-score
        PriorityQueue<Node> priorityQueue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        // Use CoordinateKey for efficient and precise position lookups
        Map<CoordinateKey, Node> allNodes = new HashMap<>();

        // Initialize start node
        double startG = 0;
        double startH = heuristic(start, goal, profile);
        double startF = calculateScore(startG, startH, profile);
        double arrivalAngle = 0; // No arrival angle for start

        Node startNode = new Node(start, null, startG, startH, startF, arrivalAngle);
        CoordinateKey startKey = CoordinateKey.fromLngLat(start);

        priorityQueue.add(startNode);
        allNodes.put(startKey, startNode);

        int iterations = 0;
        final int MAX_ITERATIONS = 1000000;

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
                return new PathResult(reconstructPath(current), current.g); // Return path and total time (g)
            }

            // Explore Neighbors using DRONE-SPECIFIC flight angles
            for (double angle : profile.getFlightAngles()) {
                // Use DRONE-SPECIFIC move distance (speed)
                LngLat nextPos = restService.nextPosition(current.position, angle, profile.getMoveDistance());
                CoordinateKey nextKey = CoordinateKey.fromLngLat(nextPos);

                // BOUNDARY CHECK: Ensure we DO NOT leave the allowed region
                if (leavesAllowedRegions(current.position, nextPos, boundaryRegions, profile.getSafetyMargin())) {
                    continue;
                }

                // 2. PHYSICS CALCULATION: Calculate Turn Angle
                double angleDiff = 0.0;
                if (current.parent != null) {
                    angleDiff = Math.abs(angle - current.arrivalAngle);
                    if (angleDiff > 180) {
                        angleDiff = 360 - angleDiff;
                    }
                }

                // 3. PHYSICS CALCULATION: Dynamic Speed (Cornering Logic)
                // Reduce speed based on how sharp the turn is
                double speedLoss = angleDiff * profile.getDragFactor();

                // Effective speed = Base Speed * (100% - Drag Loss)
                double currentSpeed = profile.getBaseSpeed() * (1.0 - speedLoss);

                // Clamp speed: Don't let speed drop below 10% of base (prevents stall/infinite cost)
                currentSpeed = Math.max(currentSpeed, profile.getBaseSpeed() * 0.1);

                // 4. CALCULATE COST (G-SCORE) AS TIME
                // Time = Distance / Speed
                double stepDistance = profile.getMoveDistance();
                double timeStep = stepDistance / currentSpeed;

                // This effectively replaces 'moveCost' and 'turnCost'
                double tentativeG = current.g + timeStep + profile.getTurningPenalty() * (angleDiff / 180.0); // Normalize turn penalty

                Node existingNode = allNodes.get(nextKey);

                // If we found a cheaper path to this neighbor (or haven't seen it yet)
                if (existingNode == null || tentativeG < existingNode.g) {
                    double h = heuristic(nextPos, goal, profile);
                    double f = calculateScore(tentativeG, h, profile);

                    // Create NEW node to avoid corrupting the PriorityQueue
                    Node newNode = new Node(nextPos, current, tentativeG, h, f, angle);

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
    private boolean leavesAllowedRegions(LngLat pos1, LngLat pos2, List<Region> allowedRegions, double safetyMargin) {
        // Quick check: if no allowed regions, any move is invalid
        if (allowedRegions.isEmpty()) return false;

        for (Region region : allowedRegions) {
            // Check if points are spatially outside the region
            if (restService.isOutsideRegion(pos1, region) || restService.isOutsideRegion(pos2, region)) {
                return true; // We are outside the allowed boundary
            }

            // 2. NEW: Safety Margin Check (The "Apex" Logic)
            // If the drone is valid but too close to the wall for this driver's skill level
            if (getDistanceToNearestWall(pos1, allowedRegions) < safetyMargin) {
                return true; // Treat this space as "solid wall" for this specific racer
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

    /**
     * Calculates the minimum distance from a given point to the nearest boundary wall
     * of any allowed region. This simulates the "Apex" proximity.
     *
     * @param pos            The position to check.
     * @param allowedRegions The list of regions defining the track/allowed area.
     * @return The distance to the nearest wall in coordinate units (degrees).
     */
    private double getDistanceToNearestWall(LngLat pos, List<Region> allowedRegions) {
        double minDistance = Double.MAX_VALUE;

        for (Region region : allowedRegions) {
            List<LngLat> vertices = region.getVertices();
            if (vertices == null || vertices.size() < 2) continue;

            // Iterate over every edge of the polygon
            for (int i = 0; i < vertices.size(); i++) {
                LngLat p1 = vertices.get(i);
                // Use modulo to wrap around and check the closing edge (last point -> first point)
                LngLat p2 = vertices.get((i + 1) % vertices.size());

                double dist = distancePointToSegment(pos, p1, p2);
                if (dist < minDistance) {
                    minDistance = dist;
                }
            }
        }
        return minDistance;
    }

    /**
     * Helper to find the shortest distance from point P to the line segment VW.
     */
    private double distancePointToSegment(LngLat p, LngLat v, LngLat w) {
        double x = p.getLongitude();
        double y = p.getLatitude();
        double x1 = v.getLongitude();
        double y1 = v.getLatitude();
        double x2 = w.getLongitude();
        double y2 = w.getLatitude();

        // Length squared of the segment
        double l2 = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);

        // If segment is a single point (length 0), just distance to that point
        if (l2 == 0) return Math.sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1));

        // Calculate projection factor t = dot(p-v, w-v) / |w-v|^2
        double t = ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / l2;

        // Clamp t to the segment range [0, 1]
        // t=0 means the closest point is v, t=1 means the closest point is w
        t = Math.max(0, Math.min(1, t));

        // Find the coordinates of the closest point on the segment
        double projX = x1 + t * (x2 - x1);
        double projY = y1 + t * (y2 - y1);

        // Return Euclidean distance from p to the projection
        return Math.sqrt((x - projX) * (x - projX) + (y - projY) * (y - projY));
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
        // Basic Distance
        double dx = Math.abs(current.getLongitude() - goal.getLongitude());
        double dy = Math.abs(current.getLatitude() - goal.getLatitude());

        double maxSpeed = profile.getBaseSpeed();

        // Prevent division by zero if baseSpeed isn't set
        if (maxSpeed <= 0) maxSpeed = 0.00015; // fallback

        return switch (profile.getHeuristicType()) {
            // Time = (dx + dy) / Speed
            case MANHATTAN -> (dx + dy) / maxSpeed;

            // Time = max(dx, dy) / Speed
            case CHEBYSHEV -> Math.max(dx, dy) / maxSpeed;

            // Time = Distance / Speed
            case EUCLIDEAN -> {
                double dist = restService.calculateDistance(current, goal);
                double h = dist / maxSpeed;
                // Optional Tie-breaker: prefer straight lines
                yield h * 1.0001; // Slightly favor nodes that are more direct
            }

            // Time = Octile Distance / Speed
            case OCTILE -> {
                double F = Math.sqrt(2) - 1.0;
                double dist = (dx + dy) + (F * Math.min(dx, dy));
                yield dist / maxSpeed;
            }
            // Fallback for types not yet handled
            default -> restService.calculateDistance(current, goal) / maxSpeed;
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