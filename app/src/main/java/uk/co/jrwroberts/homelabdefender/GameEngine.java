package uk.co.jrwroberts.homelabdefender;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GameEngine {
    private final Map<String, Incident> incidents = Map.of(
        "attack", new Incident("attack", "Suspicious request pattern", "Repeated probing is visible in reverse-proxy logs.", Action.BLOCK),
        "backup", new Incident("backup", "Backup failed", "The overnight backup did not complete.", Action.INVESTIGATE),
        "disk", new Incident("disk", "Disk pressure", "A host filesystem is almost full.", Action.INVESTIGATE),
        "patch", new Incident("patch", "Security updates available", "A managed host has pending security updates.", Action.PATCH)
    );

    public List<Incident> incidents() {
        return incidents.values().stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }

    public Optional<Resolution> resolve(String incidentId, String actionValue) {
        Incident incident = incidents.get(incidentId);
        if (incident == null || actionValue == null) {
            return Optional.empty();
        }

        try {
            Action action = Action.valueOf(actionValue.toUpperCase());
            boolean successful = incident.bestAction() == action;
            String message = successful
                ? "Good call — " + action.label() + " is the right next action."
                : "Not quite — investigate the impact and choose a safer next step.";
            return Optional.of(new Resolution(incident.id(), action, successful, message));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public enum Action {
        INVESTIGATE("Investigate"),
        RESTART("Restart"),
        PATCH("Patch"),
        BLOCK("Block"),
        RESTORE("Restore"),
        IGNORE("Ignore");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Incident(String id, String title, String description, Action bestAction) {}
    public record Resolution(String incidentId, Action action, boolean successful, String message) {}
}
