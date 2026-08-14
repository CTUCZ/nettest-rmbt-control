package at.rtr.rmbt.dto;

import at.rtr.rmbt.enums.IntegrityAction;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrityCheckOutcome {

    /** uid of the persisted test_integrity row; null when nothing was persisted. */
    private final Long recordUid;

    private final IntegrityAction action;
}
