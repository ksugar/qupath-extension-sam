package org.elephant.sam.entities;

/**
 * Available SAM output types.
 */
public enum SAMOutput {

    SINGLE_MASK, MULTI_ALL, MULTI_LARGEST, MULTI_SMALLEST, MULTI_BEST_QUALITY;

    @Override
    public String toString() {
        // Provide a more user-friendly name
        switch (this) {
            case SINGLE_MASK:
                return "Single mask";
            case MULTI_ALL:
                return "Multi-mask (all)";
            case MULTI_LARGEST:
                return "Multi-mask (largest)";
            case MULTI_SMALLEST:
                return "Multi-mask (smallest)";
            case MULTI_BEST_QUALITY:
                return "Multi-mask (best quality)";
        }
        throw new IllegalArgumentException("Unknown SAM output");
    }
}
