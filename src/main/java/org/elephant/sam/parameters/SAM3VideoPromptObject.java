package org.elephant.sam.parameters;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * Represents a SAM3 video input object.
 * This class encapsulates the information related to a video input object in the SAM3 system.
 */
public class SAM3VideoPromptObject implements SAMVideoPromptObject {

	@SuppressWarnings("unused")
	private int obj_id;
	@SuppressWarnings("unused")
	private String text;
	private double[][] boxes_xywh;
	private int[] box_labels;
	private String builderAsGroovyScript;

	public SAM3VideoPromptObject(final Builder builder) {
		Objects.requireNonNull(builder.obj_id, "Object ID must be specified");
		this.obj_id = builder.obj_id;
		this.text = builder.text;
		int nBoxes = builder.positiveBboxes.size() + builder.negativeBboxes.size();
		if (nBoxes > 0) {
			this.boxes_xywh = new double[nBoxes][4];
			this.box_labels = new int[nBoxes];
			int ind = 0;
			// positive boxes have label 1, while negative boxes have label 0
			for (double[] box : builder.negativeBboxes) {
				this.boxes_xywh[ind][0] = box[0];
				this.boxes_xywh[ind][1] = box[1];
				this.boxes_xywh[ind][2] = box[2];
				this.boxes_xywh[ind][3] = box[3];
				this.box_labels[ind] = 0;
				ind++;
			}
			for (double[] box : builder.positiveBboxes) {
				this.boxes_xywh[ind][0] = box[0];
				this.boxes_xywh[ind][1] = box[1];
				this.boxes_xywh[ind][2] = box[2];
				this.boxes_xywh[ind][3] = box[3];
				this.box_labels[ind] = 1;
				ind++;
			}
		}
		builderAsGroovyScript = builder.builderAsGroovyScript();
	}

	@Override
	public String getBuilderAsGroovyScript() {
		return builderAsGroovyScript;
	}

	/**
	 * Create a builder for a new prompt .
	 * 
	 * @param model
	 *            the SAM model
	 * @return a new builder for further customization
	 */
	public static SAM3VideoPromptObject.Builder builder(final int object_id) {
		return new Builder(object_id);
	}

	public static class Builder {
		private int obj_id;
		private String text;

		private Collection<double[]> positiveBboxes = new LinkedHashSet<>();
		private Collection<double[]> negativeBboxes = new LinkedHashSet<>();

		private Builder(final int obj_id) {
			this.obj_id = obj_id;
		};

		/**
		 * Set the text prompt for this object (optional).
		 * 
		 * @param text
		 * @return this builder
		 */
		public Builder text(final String text) {
			this.text = text;
			return this;
		}

		/**
		 * Add the specified coordinates as a positive bounding box (optional).
		 * 
		 * @param x
		 * @param y
		 * @param w
		 * @param h
		 * @return this builder
		 */
		public Builder addPositiveBbox(final double x, final double y, final double w, final double h) {
			this.positiveBboxes.add(new double[] { x, y, w, h });
			return this;
		}

		/**
		 * Add the specified coordinates as a negative bounding box (optional).
		 * 
		 * @param x
		 * @param y
		 * @param w
		 * @param h
		 * @return this builder
		 */
		public Builder addNegativeBbox(final double x, final double y, final double w, final double h) {
			this.negativeBboxes.add(new double[] { x, y, w, h });
			return this;
		}

		/**
		 * Build the prompt.
		 * 
		 * @return a prompt that should be ready to use
		 */
		public SAM3VideoPromptObject build() {
			return new SAM3VideoPromptObject(this);
		}

		public String builderAsGroovyScript() {
			StringBuilder sb = new StringBuilder();
			sb.append("org.elephant.sam.parameters.SAM3VideoPromptObject.builder(").append(obj_id).append(")");
			if (text != null) {
				sb.append(".text(\"").append(text).append("\")");
			}
			if (positiveBboxes != null && !positiveBboxes.isEmpty()) {
				positiveBboxes.forEach(
						bbox -> sb.append(
								String.format(".addPositiveBbox(%f, %f, %f, %f)", bbox[0], bbox[1], bbox[2], bbox[3])));
			}
			if (negativeBboxes != null && !negativeBboxes.isEmpty()) {
				negativeBboxes.forEach(
						bbox -> sb.append(
								String.format(".addNegativeBbox(%f, %f, %f, %f)", bbox[0], bbox[1], bbox[2], bbox[3])));
			}
			return sb.toString();
		}
	}
}
