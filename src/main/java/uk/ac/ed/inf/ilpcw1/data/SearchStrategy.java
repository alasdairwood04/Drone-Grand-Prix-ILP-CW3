package uk.ac.ed.inf.ilpcw1.data;

import lombok.Getter;

@Getter
public enum SearchStrategy {
    ASTAR("A Star"),       // f = g + h
    GREEDY("Greedy"),          // f = h (ignores cost so far)
    DIJKSTRA("Dijkstra"),    // f = g (ignores heuristic)
    WEIGHTED_ASTAR("W A Star"); // f = g + w*h

    private final String personalityName;

    SearchStrategy(String personalityName) {
        this.personalityName = personalityName;
    }

}