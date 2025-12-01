package uk.ac.ed.inf.ilpcw1.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RacerProfile {
    private String name;
    private String color;
    private SearchStrategy strategy;

    // SPEED/AGILITY PARAMETERS
    private double turningPenalty; // cost added per degree of turn

    // safety distance from obstacles (rookie would be larger than pro)
    private double safetyMargin;

    private double baseSpeed; // meters per tick

    private double dragFactor; // how much is lost per degree of turn

    private double moveDistance;

    // allowed flight directions
    private double[] flightAngles;


    private double heuristicWeight;

    @Builder.Default
    private HeuristicType heuristicType = HeuristicType.EUCLIDEAN;
}