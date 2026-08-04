package com.navalrivals.infra.cluster;

public record ClusterMessage(String destination, String payloadJson, String userId) {

    // Broadcast (userId = null)
    public ClusterMessage(String destination, String payloadJson) {
        this(destination, payloadJson, null);
    }

    public boolean isUserSpecific() {
        return userId != null;
    }
}
