package org.elephant.sam.entities;

/*
 * SAM progress.
 */
public class SAMProgress {

    final String message;

    final int percent;

    /**
     * Constructor for SAM progress.
     * 
     * @param message
     * @param percent
     */
    public SAMProgress(String message, int percent) {
        this.message = message;
        this.percent = percent;
    }

    /**
     * Get the message.
     * 
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the percent.
     * 
     * @return the percent
     */
    public int getPercent() {
        return percent;
    }

}
