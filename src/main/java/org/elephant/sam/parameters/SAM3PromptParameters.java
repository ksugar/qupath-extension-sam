package org.elephant.sam.parameters;

import org.elephant.sam.entities.SAMType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Prompt that is sent to the server for annotation, as JSON.
 */
public class SAM3PromptParameters {

	@SuppressWarnings("unused")
	private String type;
	@SuppressWarnings("unused")
	private String b64img;
	@SuppressWarnings("unused")
	private String text_prompt;
	@SuppressWarnings("unused")
	private List<int[]> positive_bboxes;
	@SuppressWarnings("unused")
	private List<int[]> negative_bboxes;
	@SuppressWarnings("unused")
	private String checkpoint_url;
	@SuppressWarnings("unused")
	private boolean reset_prompts;
	@SuppressWarnings("unused")
	private double confidence_threshold;

	private SAM3PromptParameters(final Builder builder) {
		Objects.requireNonNull(builder.type, "Model type must be specified");
		Objects.requireNonNull(builder.b64img, "Input image must be specified");
		this.type = builder.type;
		this.b64img = builder.b64img;
		if (builder.positive_bboxes != null)
			this.positive_bboxes = builder.positive_bboxes;
		else
			this.positive_bboxes = List.of();
		if (builder.negative_bboxes != null)
			this.negative_bboxes = builder.negative_bboxes;
		else
			this.negative_bboxes = List.of();
		this.text_prompt = builder.textPrompt;
		this.checkpoint_url = builder.checkpointUrl;
		this.reset_prompts = builder.resetPrompts;
		this.confidence_threshold = builder.confidenceThresh;
	}

	/**
	 * Create a builder for a new prompt.
	 * 
	 * @param model
	 *            the SAM model
	 * @return a new builder for further customization
	 */
	public static SAM3PromptParameters.Builder builder(final SAMType model) {
		return new Builder(model);
	}

	public static class Builder {
		private String type;
		private String b64img;
		private List<int[]> positive_bboxes = new ArrayList<>();
		private List<int[]> negative_bboxes = new ArrayList<>();
		private String textPrompt;
		private String checkpointUrl;
		private boolean resetPrompts = false;
		private double confidenceThresh = 0.4;

		private Builder(final SAMType model) {
			this.type = model.modelName();
		};

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
		 * Text prompt (optional).
		 * 
		 * @param textPrompt
		 * @return this builder
		 */
		public Builder textPrompt(final String textPrompt) {
			this.textPrompt = textPrompt;
			return this;
		}

		/**
		 * Bounding box, used for providing a rectangular positive prompt (optional).
		 * Coordinates should be in the input image space.
		 * 
		 * @param x1
		 * @param y1
		 * @param x2
		 * @param y2
		 * @return this builder
		 */
		public Builder addPositiveBbox(final int x1, final int y1, final int x2, final int y2) {
			this.positive_bboxes.add(new int[] { x1, y1, x2, y2 });
			return this;
		}

		/**
		 * Bounding box, used for providing a rectangular negative prompt (optional).
		 * Coordinates should be in the input image space.
		 * 
		 * @param x1
		 * @param y1
		 * @param x2
		 * @param y2
		 * @return this builder
		 */
		public Builder addNegativeBbox(final int x1, final int y1, final int x2, final int y2) {
			this.negative_bboxes.add(new int[] { x1, y1, x2, y2 });
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
		 * Whether to reset prompts (optional).
		 * 
		 * @param resetPrompts
		 * @return this builder
		 */
		public Builder resetPrompts(boolean resetPrompts) {
			this.resetPrompts = resetPrompts;
			return this;
		}

		/**
		 * Confidence threshold in [0, 1] (optional).
		 * 
		 * @param confidenceThresh
		 * @return this builder
		 */
		public Builder confidenceThresh(double confidenceThresh) {
			this.confidenceThresh = confidenceThresh;
			return this;
		}

		/**
		 * Build the prompt.
		 * 
		 * @return a prompt that should be ready to use
		 */
		public SAM3PromptParameters build() {
			return new SAM3PromptParameters(this);
		}
	}

}
