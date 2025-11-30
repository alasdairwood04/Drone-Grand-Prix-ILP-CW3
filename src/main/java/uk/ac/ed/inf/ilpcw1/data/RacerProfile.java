package uk.ac.ed.inf.ilpcw1.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RacerProfile {
    private String name;
    private String color;
    private SearchStrategy strategy; // e.g., A*, Greedy, Dijkstra

    // SPEED/AGILITY PARAMETERS
    private double turningPenalty; // cost added per degree of turn

    // safety distance from obstacles (rookie would be larger than pro)
    private double safetyMargin;

    private double baseSpeed; // meters per tick

    private double dragFactor; // how much is lost per degree of turn

    // PHYSICS PROPERTIES
    private double moveDistance;    // e.g., 0.00015 (Standard) vs 0.00030 (Fast)

    // allowed flight directions (e.g., 16 standard, or just 4 for a "rook" movement)
    private double[] flightAngles;


    // changing heuristic weight to modify A* behavior
    private double heuristicWeight;

    @Builder.Default
    private HeuristicType heuristicType = HeuristicType.EUCLIDEAN;
}