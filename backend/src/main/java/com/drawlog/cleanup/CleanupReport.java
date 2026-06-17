package com.drawlog.cleanup;

import java.util.List;

public record CleanupReport(
        boolean apply,
        int orphanUploadCandidates,
        int deletedUploadFiles,
        int deletedMemberships,
        int deletedDrawings,
        int deletedDrawingFiles,
        int ownerRepairedGroups,
        int deletedEmptyGroups,
        int deletedGroupDrawings
) {
    public List<String> lines() {
        return List.of(
                "Drawlog cleanup " + (apply ? "APPLY" : "DRY-RUN"),
                "orphan upload candidates: " + orphanUploadCandidates,
                "deleted upload files: " + deletedUploadFiles,
                "deleted DELETED memberships: " + deletedMemberships,
                "deleted DELETED drawings: " + deletedDrawings,
                "deleted DELETED drawing files: " + deletedDrawingFiles,
                "owner-repaired groups: " + ownerRepairedGroups,
                "deleted empty groups: " + deletedEmptyGroups,
                "deleted drawings from empty groups: " + deletedGroupDrawings
        );
    }
}
