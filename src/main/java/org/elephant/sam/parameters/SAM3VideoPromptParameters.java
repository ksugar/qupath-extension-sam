package org.elephant.sam.parameters;

import org.elephant.sam.entities.SAMType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prompt that is sent to the server for annotation, as JSON.
 */
public class SAM3VideoPromptParameters {

	@SuppressWarnings("unused")
	private String type;
	@SuppressWarnings("unused")
	private String dirname;
	@SuppressWarnings("unused")
	private String axes;
	@SuppressWarnings("unused")
	private int plane_position;
	@SuppressWarnings("unused")
	private Map<Integer, List<SAM3VideoPromptObject>> objs;
	@SuppressWarnings("unused")
	private String checkpointUrl;

	private SAM3VideoPromptParameters(final Builder builder) {
		Objects.requireNonNull(builder.type, "Model type must be specified");
		Objects.requireNonNull(builder.dirname, "Directory name must be specified");
		this.type = builder.type;
		this.dirname = builder.dirname;
		this.axes = builder.axes;
		this.plane_position = builder.planePosition;
		this.objs = builder.objs;
		this.checkpointUrl = builder.checkpointUrl;
	}

	/**
	 * Create a builder for a new prompt.
	 * 
	 * @param model
	 *            the SAM model
	 * @return a new builder for further customization
	 */
	public static SAM3VideoPromptParameters.Builder builder(final SAMType model) {
		return new Builder(model);
	}

	public static class Builder {
		private String type;
		private String dirname;
		private String axes;
		private int planePosition;
		private Map<Integer, List<SAM3VideoPromptObject>> objs;
		private String checkpointUrl;

		private Builder(final SAMType model) {
			this.type = model.modelName();
		};

		/**
		 * Diretory name containing the video files on the server (required).
		 * 
		 * @param dirname
		 * @return this builder
		 */
		public Builder dirname(final String dirname) {
			this.dirname = dirname;
			return this;
		}

		/**
		 * Objects to annotate (required).
		 * 
		 * @param objs
		 * @return this builder
		 */
		public Builder objs(final Map<Integer, List<SAM3VideoPromptObject>> objs) {
			this.objs = objs;
			return this;
		}

		/**
		 * Axes to process ("XYZ" or "XYT").
		 * 
		 * @param axes
		 * @return this builder
		 */
		public Builder axes(final String axes) {
			this.axes = axes;
			return this;
		}

		/**
		 * Plane position (required).
		 * 
		 * @param planePosition
		 * @return this builder
		 */
		public Builder planePosition(final int planePosition) {
			this.planePosition = planePosition;
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
		public SAM3VideoPromptParameters build() {
			return new SAM3VideoPromptParameters(this);
		}
	}

}
