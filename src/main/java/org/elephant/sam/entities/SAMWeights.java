package org.elephant.sam.entities;

/**
 * SAM weights.
 */
public class SAMWeights {

    private final String type;

    private final String name;

    private final String url;

    public SAMWeights(String type, String name, String url) {
        this.type = type;
        this.name = name;
        this.url = url;
    }

    /**
     * Model type as used in the SAM code.
     * 
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * Model name as used in the SAM code.
     * 
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * URL to download the weights.
     * 
     * @return
     */
    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return name + " (" + url + ")";
    }

}
