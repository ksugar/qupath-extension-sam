package org.elephant.sam;

public class SAMSetup {
    private static final SAMSetup instance = new SAMSetup();
    private String SAMUrl = null;

    public static SAMSetup getInstance() {
        return instance;
    }
    public String getSAMUrl() {
        return SAMUrl;
    }

    public void setSAMUrl(String url) {
        this.SAMUrl = url;
    }
}
