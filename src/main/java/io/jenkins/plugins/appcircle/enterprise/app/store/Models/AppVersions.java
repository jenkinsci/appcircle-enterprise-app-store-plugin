package io.jenkins.plugins.appcircle.enterprise.app.store.Models;

import java.time.LocalDateTime;

public class AppVersions {
    private final String id;
    private final LocalDateTime updateDate;

    public AppVersions(String id, LocalDateTime updateDate) {
        this.id = id;
        this.updateDate = updateDate;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getUpdateDate() {
        return updateDate;
    }
}
