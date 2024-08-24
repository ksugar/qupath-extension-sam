package org.elephant.sam.parameters;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

import org.locationtech.jts.geom.Coordinate;

/**
 * Represents a SAM2 video input object.
 * This class encapsulates the information related to a video input object in the SAM2 system.
 */
public class SAM2VideoPromptObject {

	@SuppressWarnings("unused")
	private int obj_id;
	private int[][] point_coords;
	private int[] point_labels;
	@SuppressWarnings("unused")
	private int[] bbox;

	public SAM2VideoPromptObject(final Builder builder) {
		Objects.requireNonNull(builder.obj_id, "Object ID must be specified");
		this.obj_id = builder.obj_id;
		this.bbox = builder.bbox;
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
	}

	/**
	 * Create a builder for a new prompt .
	 * 
	 * @param model
	 *            the SAM model
	 * @return a new builder for further customization
	 */
	public static SAM2VideoPromptObject.Builder builder(final int object_id) {
		return new Builder(object_id);
	}

	public static class Builder {
		private int obj_id;
		private int[] bbox;

		private Collection<Coordinate> foreground = new LinkedHashSet<>();
		private Collection<Coordinate> background = new LinkedHashSet<>();

		private Builder(final int obj_id) {
			this.obj_id = obj_id;
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
		 * Build the prompt.
		 * 
		 * @return a prompt that should be ready to use
		 */
		public SAM2VideoPromptObject build() {
			return new SAM2VideoPromptObject(this);
		}
	}
}
