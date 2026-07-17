package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateRelease;

import java.util.List;

public record RenovateDatasourceDto(List<RenovateRelease> releases) {
}
