package org.elephant.sam;

/**
 * Available SAM models.
 */
public enum SAMModel {

    VIT_H, VIT_L, VIT_B;

    /**
     * Model name as used in the SAM code.
     * @return
     */
    public String modelName() {
        switch (this) {
            case VIT_H:
                return "vit_h";
            case VIT_L:
                return "vit_l";
            case VIT_B:
                return "vit_b";
        }
        throw new IllegalArgumentException("Unknown SAM model");
    }

    @Override
    public String toString() {
        // Provide a more user-friendly name
        switch (this) {
            case VIT_H:
                return "vit_h (huge)";
            case VIT_L:
                return "vit_l (large)";
            case VIT_B:
                return "vit_b (base)";
        }
        throw new IllegalArgumentException("Unknown SAM model");
    }
}
