package com.krynhild.polishwritinglab.ingestion.model;

import java.util.List;

/**
 * Remarks the answer key makes about a whole task.
 *
 * @param distractors options that fit no gap, from {@code Wyraz zbędny: …}
 * @param acceptOtherValid the key says to accept other correct answers too
 * @param acceptOtherValidScope what those answers must agree with: {@code instruction} or {@code transcript}
 */
public record KeyNotes(List<String> distractors, boolean acceptOtherValid, String acceptOtherValidScope, List<String> notes) {
}
