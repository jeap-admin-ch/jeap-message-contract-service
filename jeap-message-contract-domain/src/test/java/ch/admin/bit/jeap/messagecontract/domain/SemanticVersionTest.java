package ch.admin.bit.jeap.messagecontract.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SemanticVersionTest {

    @Test
    void comparesNumericVersionParts() {
        assertThat(SemanticVersion.parse("1.10.0"))
                .isGreaterThan(SemanticVersion.parse("1.9.0"));
        assertThat(SemanticVersion.parse("2.0.0"))
                .isGreaterThan(SemanticVersion.parse("1.99.99"));
        assertThat(SemanticVersion.parse("1.0.1"))
                .isGreaterThan(SemanticVersion.parse("1.0.0"));
    }

    @Test
    void retainsOriginalValue() {
        assertThat(SemanticVersion.parse("1.2.3").value()).isEqualTo("1.2.3");
    }

    @Test
    void versionsWithEqualPartsAreEqual() {
        assertThat(SemanticVersion.parse("1.2.3"))
                .isEqualTo(SemanticVersion.parse("1.2.3"))
                .hasSameHashCodeAs(SemanticVersion.parse("1.2.3"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1.0", "1.0.0-SNAPSHOT", "01.0.0", "-1.0.0"})
    void rejectsInvalidVersions(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> SemanticVersion.parse(value));
    }
}
