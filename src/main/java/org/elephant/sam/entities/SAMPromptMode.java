package org.elephant.sam.entities;

/**
 * Available SAM prompt modes.
 */
public enum SAMPromptMode {

    XY, XYZ, XYT;

    @Override
    public String toString() {
        // Provide a more user-friendly name
        switch (this) {
            case XY:
                return "XY";
            case XYZ:
                return "XYZ";
            case XYT:
                return "XYT";
        }
        throw new IllegalArgumentException("Unknown SAM prompt mode");
    }
}
