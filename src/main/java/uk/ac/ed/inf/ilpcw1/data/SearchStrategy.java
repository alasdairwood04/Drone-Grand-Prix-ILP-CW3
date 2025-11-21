package uk.ac.ed.inf.ilpcw1.data;

import lombok.Getter;

@Getter
public enum SearchStrategy {
    ASTAR("Optimal Ace"),       // f = g + h
    GREEDY("Gambler"),          // f = h (ignores cost so far)
    DIJKSTRA("Safe Driver");    // f = g (ignores heuristic)

    private final String personalityName;

    SearchStrategy(String personalityName) {
        this.personalityName = personalityName;
    }

}