package com.krynhild.polishwritinglab.ingestion.model;

/** An answer option, for example {@code b) w księgarni.} or fragment {@code E} of a gapped text. */
public record Option(String id, String text) {
}
