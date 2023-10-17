package org.elephant.sam.parameters;

import java.util.Objects;

import org.elephant.sam.entities.SAMType;

/**
 * Parameters that is sent to the server for SAM auto mask, as JSON.
 */
public class SAMAutoMaskParameters {

	@SuppressWarnings("unused")
	private String type;
	@SuppressWarnings("unused")
	private String b64img;
	@SuppressWarnings("unused")
	private int points_per_side;
	@SuppressWarnings("unused")
	private int points_per_batch;
	@SuppressWarnings("unused")
	private double pred_iou_thresh;
	@SuppressWarnings("unused")
	private double stability_score_thresh;
	@SuppressWarnings("unused")
	private double stability_score_offset;
	@SuppressWarnings("unused")
	private double box_nms_thresh;
	@SuppressWarnings("unused")
	private int crop_n_layers;
	@SuppressWarnings("unused")
	private double crop_nms_thresh;
	@SuppressWarnings("unused")
	private double crop_overlap_ratio;
	@SuppressWarnings("unused")
	private int crop_n_points_downscale_factor;
	@SuppressWarnings("unused")
	private int min_mask_region_area;
	@SuppressWarnings("unused")
	private String output_type;
	@SuppressWarnings("unused")
	private boolean include_image_edge;
	@SuppressWarnings("unused")
	private String checkpoint_url;

	private SAMAutoMaskParameters(final Builder builder) {
		Objects.requireNonNull(builder.type, "Model type must be specified");
		Objects.requireNonNull(builder.b64img, "Input image must be specified");
		this.type = builder.type;
		this.b64img = builder.b64img;
		this.points_per_side = builder.pointsPerSide;
		this.points_per_batch = builder.pointsPerBatch;
		this.pred_iou_thresh = builder.predIoUThresh;
		this.stability_score_thresh = builder.stabilityScoreThresh;
		this.stability_score_offset = builder.stabilityScoreOffset;
		this.box_nms_thresh = builder.boxNmsThresh;
		this.crop_n_layers = builder.cropNLayers;
		this.crop_nms_thresh = builder.cropNmsThresh;
		this.crop_overlap_ratio = builder.cropOverlapRatio;
		this.crop_n_points_downscale_factor = builder.cropNPointsDownscaleFactor;
		this.min_mask_region_area = builder.minMaskRegionArea;
		this.output_type = builder.outputType;
		this.include_image_edge = builder.includeImageEdge;
		this.checkpoint_url = builder.checkpointUrl;
	}

	/**
	 * Create a builder for a new prompt.
	 * 
	 * @param model
	 *            the SAM model
	 * @return a new builder for further customization
	 */
	public static SAMAutoMaskParameters.Builder builder(final SAMType model) {
		return new Builder(model);
	}

	public static class Builder {
		private String type;
		private String b64img;
		private int pointsPerSide;
		private int pointsPerBatch;
		private double predIoUThresh;
		private double stabilityScoreThresh;
		private double stabilityScoreOffset;
		private double boxNmsThresh;
		private int cropNLayers;
		private double cropNmsThresh;
		private double cropOverlapRatio;
		private int cropNPointsDownscaleFactor;
		private int minMaskRegionArea;
		private String outputType;
		private boolean includeImageEdge;
		private String checkpointUrl;

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
		 * The number of points to be sampled along one side of the image.
		 * The total number of points is points_per_side**2.
		 * 
		 * @param pointsPerSide
		 * @return this builder
		 */
		public Builder pointsPerSide(final int pointsPerSide) {
			this.pointsPerSide = pointsPerSide;
			return this;
		}

		/**
		 * Sets the number of points run simultaneously by the model.
		 * Higher numbers may be faster but use more GPU memory.
		 * 
		 * @param pointsPerBatch
		 * @return this builder
		 */
		public Builder pointsPerBatch(final int pointsPerBatch) {
			this.pointsPerBatch = pointsPerBatch;
			return this;
		}

		/**
		 * A filtering threshold in [0,1], using the model's predicted mask quality.
		 * 
		 * @param predIoUThresh
		 * @return this builder
		 */
		public Builder predIoUThresh(final double predIoUThresh) {
			this.predIoUThresh = predIoUThresh;
			return this;
		}

		/**
		 * A filtering threshold in [0,1], using the stability of the mask under changes to the cutoff
		 * used to binarize the model's mask predictions.
		 * 
		 * @param stabilityScoreThresh
		 * @return this builder
		 */
		public Builder stabilityScoreThresh(final double stabilityScoreThresh) {
			this.stabilityScoreThresh = stabilityScoreThresh;
			return this;
		}

		/**
		 * The amount to shift the cutoff when calculated the stability score.
		 * 
		 * @param stabilityScoreOffset
		 * @return this builder
		 */
		public Builder stabilityScoreOffset(final double stabilityScoreOffset) {
			this.stabilityScoreOffset = stabilityScoreOffset;
			return this;
		}

		/**
		 * The box IoU cutoff used by non-maximal suppression to filter duplicate masks.
		 * 
		 * @param boxNmsThresh
		 * @return this builder
		 */
		public Builder boxNmsThresh(final double boxNmsThresh) {
			this.boxNmsThresh = boxNmsThresh;
			return this;
		}

		/**
		 * If >0, mask prediction will be run again on crops of the image.
		 * Sets the number of layers to run, where each layer has 2**i_layer number of image crops.
		 * 
		 * @param cropNLayers
		 * @return this builder
		 */
		public Builder cropNLayers(final int cropNLayers) {
			this.cropNLayers = cropNLayers;
			return this;
		}

		/**
		 * The box IoU cutoff used by non-maximal suppression to filter duplicate masks between different crops.
		 * 
		 * @param cropNmsThresh
		 * @return this builder
		 */
		public Builder cropNmsThresh(final double cropNmsThresh) {
			this.cropNmsThresh = cropNmsThresh;
			return this;
		}

		/**
		 * Sets the degree to which crops overlap. In the first crop layer,
		 * crops will overlap by this fraction of the image length.
		 * Later layers with more crops scale down this overlap.
		 * 
		 * @param cropOverlapRatio
		 * @return this builder
		 */
		public Builder cropOverlapRatio(final double cropOverlapRatio) {
			this.cropOverlapRatio = cropOverlapRatio;
			return this;
		}

		/**
		 * The number of points-per-side sampled in layer n is scaled down by crop_n_points_downscale_factor**n.
		 * 
		 * @param cropNPointsDownscaleFactor
		 * @return this builder
		 */
		public Builder cropNPointsDownscaleFactor(final int cropNPointsDownscaleFactor) {
			this.cropNPointsDownscaleFactor = cropNPointsDownscaleFactor;
			return this;
		}

		/**
		 * If >0, postprocessing will be applied to remove disconnected regions and holes in masks with area
		 * smaller than min_mask_region_area. Requires opencv.
		 * 
		 * @param minMaskRegionArea
		 * @return this builder
		 */
		public Builder minMaskRegionArea(final int minMaskRegionArea) {
			this.minMaskRegionArea = minMaskRegionArea;
			return this;
		}

		/**
		 * How to deal with SAM masks.
		 * 
		 * @param outputType
		 * @return this builder
		 */
		public Builder outputType(final String outputType) {
			this.outputType = outputType;
			return this;
		}

		/**
		 * If specified, include image edge in SAM auto mask generator.
		 * 
		 * @param includeImageEdge
		 * @return this builder
		 */
		public Builder includeImageEdge(final boolean includeImageEdge) {
			this.includeImageEdge = includeImageEdge;
			return this;
		}

		/**
		 * If specified, use the specified checkpoint.
		 * 
		 * @param checkpointUrl
		 * @return this builder
		 */
		public Builder checkpointUrl(final String checkpointUrl) {
			this.checkpointUrl = checkpointUrl;
			return this;
		}

		/**
		 * Build the parameters.
		 * 
		 * @return parameters that are sent to the server
		 */
		public SAMAutoMaskParameters build() {
			return new SAMAutoMaskParameters(this);
		}
	}

}
