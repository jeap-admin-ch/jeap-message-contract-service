package ch.admin.bit.jeap.messagecontract.domain;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)");

    private final String value;
    private final int major;
    private final int minor;
    private final int patch;

    private SemanticVersion(String value, int major, int minor, int patch) {
        this.value = value;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static SemanticVersion parse(String value) {
        if (value == null || !VERSION_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Expected semantic version x.y.z: " + value);
        }
        String[] parts = value.split("\\.", -1);
        try {
            return new SemanticVersion(value, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected semantic version x.y.z: " + value, ex);
        }
    }

    public String value() {
        return value;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        return Comparator.comparingInt((SemanticVersion version) -> version.major)
                .thenComparingInt(version -> version.minor)
                .thenComparingInt(version -> version.patch)
                .compare(this, other);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SemanticVersion other)) {
            return false;
        }
        return major == other.major && minor == other.minor && patch == other.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }
}
