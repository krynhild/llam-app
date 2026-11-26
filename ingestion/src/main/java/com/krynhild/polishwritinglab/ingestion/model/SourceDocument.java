package com.krynhild.polishwritinglab.ingestion.model;

/**
 * A PDF the corpus was built from.
 *
 * @param kind {@code sheet}, {@code key} or {@code standards}
 * @param path path relative to the input folder
 */
public record SourceDocument(String kind, String path, String sha256, int pages, String producer, String url) {
}
