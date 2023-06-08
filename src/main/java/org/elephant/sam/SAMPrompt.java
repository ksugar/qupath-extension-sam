package org.elephant.sam;


public class SAMPrompt {
	
	@SuppressWarnings("unused")
	private String type;
	@SuppressWarnings("unused")
	private int[] offset;
	@SuppressWarnings("unused")
	private int[] bbox;
	@SuppressWarnings("unused")
	private double downsample;
	@SuppressWarnings("unused")
	private String b64img;
	
	public SAMPrompt(final Builder builder) {
		this.type = builder.type;
		this.offset = builder.offset;
		this.bbox = builder.bbox;
		this.downsample = builder.downsample;
		this.b64img = builder.b64img;
	}
	
	static class Builder {
		private String type;
		private int[] offset;
		private int[] bbox;
		private double downsample = 1.0;
		private String b64img;
		
		public Builder(final String type) {
			this.type = type;
		};
		
		public Builder offset(final int x, final int y) {
			this.offset = new int[]{x, y};
			return this;
		}
		
		public Builder bbox(final int x1, final int y1, final int x2, final int y2) {
			this.bbox = new int[]{x1, y1, x2, y2};
			return this;
		}
		
		public Builder downsample(final double downsample) {
			this.downsample = downsample;
			return this;
		}
		
		public Builder b64img(final String b64img) {
			this.b64img = b64img;
			return this;
		}
		
		public SAMPrompt build() {
			return new SAMPrompt(this);
		}
	}
	
	public static SAMPrompt.Builder newBuilder(final String type) {
		return new Builder(type);
	}

}
