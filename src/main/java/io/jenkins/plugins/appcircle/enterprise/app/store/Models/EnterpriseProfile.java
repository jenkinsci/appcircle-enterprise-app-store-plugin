package io.jenkins.plugins.appcircle.enterprise.app.store.Models;

import java.time.LocalDateTime;

public class EnterpriseProfile {
    private final String id;
    private final String name;
    private LocalDateTime lastBinaryReceivedDate;

    public EnterpriseProfile(String id, String name, LocalDateTime lastBinaryReceivedDate) {
        this.id = id;
        this.name = name;
        this.lastBinaryReceivedDate = lastBinaryReceivedDate;
    }

    // Getters for id and name
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getLastBinaryReceivedDate() {
        return lastBinaryReceivedDate;
    }
}
