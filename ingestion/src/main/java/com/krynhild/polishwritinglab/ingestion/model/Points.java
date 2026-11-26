package com.krynhild.polishwritinglab.ingestion.model;

/**
 * The score line of a task, for example {@code _____ / 7 p. (14 x 0,5 p.)}.
 *
 * @param count the number of scored answers, if the score line states it
 * @param perItem points per scored answer, if the score line states it
 */
public record Points(double total, Integer count, Double perItem) {
}
