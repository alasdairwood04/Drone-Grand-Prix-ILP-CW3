package uk.ac.ed.inf.ilpcw1.service;

import uk.ac.ed.inf.ilpcw1.data.LngLat;

/**
 * Node class for pathfinding algorithms.
 * Stores position, parent lineage, and cost metrics.
 */


public class Node {
    LngLat position;
    Node parent;


    double g; // Actual cost from start to this node (step count)
    double h; // Heuristic cost from this node to goal
    double f; // Priority score used by the queue (depends on strategy)

    /**
     * Constructor with explicit f-score.
     * @param position Current geographical position
     * @param parent Parent node in the path
     * @param g Actual cost from start
     * @param h Heuristic estimate to goal
     * @param f Priority score (e.g., g+h for A*, h for Greedy, g for Dijkstra)
     */

    public final double arrivalAngle; // the angle used to rach this node

    Node(LngLat position, Node parent, double g, double h, double f, double arrivalAngle) {
        this.position = position;
        this.parent = parent;
        this.g = g;
        this.h = h;
        this.f = f;
        this.arrivalAngle = arrivalAngle;
    }
}