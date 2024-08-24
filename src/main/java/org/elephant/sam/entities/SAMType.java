package org.elephant.sam.entities;

/**
 * Available SAM models.
 */
public enum SAMType {

    VIT_H, VIT_L, VIT_B, VIT_T, SAM2_L, SAM2_BP, SAM2_S, SAM2_T;

    /**
     * Model name as used in the SAM code.
     * 
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
            case VIT_T:
                return "vit_t";
            case SAM2_L:
                return "sam2_l";
            case SAM2_BP:
                return "sam2_bp";
            case SAM2_S:
                return "sam2_s";
            case SAM2_T:
                return "sam2_t";
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
            case VIT_T:
                return "vit_t (mobile)";
            case SAM2_L:
                return "sam2_l (large)";
            case SAM2_BP:
                return "sam2_bp (base plus)";
            case SAM2_S:
                return "sam2_s (small)";
            case SAM2_T:
                return "sam2_t (tiny)";
        }
        throw new IllegalArgumentException("Unknown SAM model");
    }

    public boolean isVideoCompatible() {
        switch (this) {
            case SAM2_L:
            case SAM2_BP:
            case SAM2_S:
            case SAM2_T:
                return true;
            default:
                return false;
        }
    }
}
