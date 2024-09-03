package org.elephant.sam.parameters;

import org.elephant.sam.entities.SAMType;
import org.locationtech.jts.geom.Coordinate;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * Prompt that is sent to the server for annotation, as JSON.
 */
public class SAMPromptParameters {

	@SuppressWarnings("unused")
	private String type;
	@SuppressWarnings("unused")
	private int[] bbox;
	@SuppressWarnings("unused")
	private String b64img;
	@SuppressWarnings("unused")
	private String b64mask;
	private int[][] point_coords;
	private int[] point_labels;
	@SuppressWarnings("unused")
	private boolean multimask_output;
	@SuppressWarnings("unused")
	private String checkpoint_url;

	private SAMPromptParameters(final Builder builder) {
		Objects.requireNonNull(builder.type, "Model type must be specified");
		Objects.requireNonNull(builder.b64img, "Input image must be specified");
		this.type = builder.type;
		this.bbox = builder.bbox;
		this.b64img = builder.b64img;
		this.b64mask = builder.b64mask;
		this.multimask_output = builder.multimask_output;
		int nCoords = builder.foreground.size() + builder.background.size();
		if (nCoords > 0) {
			this.point_coords = new int[nCoords][2];
			this.point_labels = new int[nCoords];
			int ind = 0;
			for (Coordinate c : builder.background) {
				this.point_coords[ind][0] = (int) c.x;
				this.point_coords[ind][1] = (int) c.y;
				this.point_labels[ind] = 0;
				ind++;
			}
			for (Coordinate c : builder.foreground) {
				this.point_coords[ind][0] = (int) c.x;
				this.point_coords[ind][1] = (int) c.y;
				this.point_labels[ind] = 1;
				ind++;
			}
		}
		this.checkpoint_url = builder.checkpointUrl;
	}

	/**
	 * Create a builder for a new prompt.
	 * 
	 * @param model
	 *            the SAM model
	 * @return a new builder for further customization
	 */
	public static SAMPromptParameters.Builder builder(final SAMType model) {
		return new Builder(model);
	}

	public static class Builder {
		private String type;
		private int[] bbox;
		private String b64img;
		private String b64mask;
		private boolean multimask_output = false;
		private String checkpointUrl;

		private Collection<Coordinate> foreground = new LinkedHashSet<>();
		private Collection<Coordinate> background = new LinkedHashSet<>();

		private Builder(final SAMType model) {
			this.type = model.modelName();
		};

		/**
		 * Bounding box, used for providing a rectangular foreground prompt (optional).
		 * Coordinates should be in the input image space.
		 * 
		 * @param x1
		 * @param y1
		 * @param x2
		 * @param y2
		 * @return this builder
		 */
		public Builder bbox(final int x1, final int y1, final int x2, final int y2) {
			this.bbox = new int[] { x1, y1, x2, y2 };
			return this;
		}

		/**
		 * Base64-encoded image (required).
		 * 
		 * @param b64img
		 * @return this builder
		 */
		public Builder b64img(final String b64img) {
			this.b64img = b64img;
			return this;
		}

		/**
		 * Base64-encoded prompt mask (optional).
		 * 
		 * @param b64mask
		 * @return this builder
		 */
		public Builder b64mask(final String b64mask) {
			this.b64mask = b64mask;
			return this;
		}

		/**
		 * Add the specified coordinates as foreground prompts (optional).
		 * 
		 * @param coords
		 * @return this builder
		 */
		public Builder addToForeground(Collection<? extends Coordinate> coords) {
			foreground.addAll(coords);
			return this;
		}

		/**
		 * Add the specified coordinates as background prompts (optional).
		 * 
		 * @param coords
		 * @return this builder
		 */
		public Builder addToBackground(Collection<? extends Coordinate> coords) {
			background.addAll(coords);
			return this;
		}

		/**
		 * Request multiple outputs (optional).
		 * Currently, this means that up to three prediction masks may be returned.
		 * 
		 * @param doMultimask
		 * @return this builder
		 */
		public Builder multimaskOutput(boolean doMultimask) {
			this.multimask_output = doMultimask;
			return this;
		}

		/**
		 * URL to a checkpoint file (optional).
		 * 
		 * @param checkpointUrl
		 * @return this builder
		 */
		public Builder checkpointUrl(String checkpointUrl) {
			this.checkpointUrl = checkpointUrl;
			return this;
		}

		/**
		 * Build the prompt.
		 * 
		 * @return a prompt that should be ready to use
		 */
		public SAMPromptParameters build() {
			return new SAMPromptParameters(this);
		}
	}

}
