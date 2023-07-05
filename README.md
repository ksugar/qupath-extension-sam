# QuPath extension SAM

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-samapi.gif" width="768">

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-multipoint-live.gif" width="768">

This is a [QuPath](https://qupath.github.io) extension for [Segment Anything Model (SAM)](https://github.com/facebookresearch/segment-anything).

This is a part of the following paper. Please [cite](#citation) it when you use this project. You will also cite [the original SAM paper](https://arxiv.org/abs/2304.02643) and [the MobileSAM paper](https://arxiv.org/abs/2306.14289).

- Sugawara, K. [*Training deep learning models for cell image segmentation with sparse annotations.*](https://biorxiv.org/cgi/content/short/2023.06.13.544786v1) bioRxiv 2023. doi:10.1101/2023.06.13.544786

## Install

Drag and drop [the extension file](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.3.0/qupath-extension-sam-0.3.0.jar) to [QuPath](https://qupath.github.io) and restart it.

Please note that you need to set up the server following the instructions in the link below.

[https://github.com/ksugar/samapi](https://github.com/ksugar/samapi)

**Please note that you need to connect to a server running [samapi v0.3](https://github.com/ksugar/samapi/tree/v0.3.0).**

## Usage

### SAM prompt command

#### Rectangle (BBox) prompt

1. Select `Extensions` > `SAM` > `SAM prompt` from the menu bar.
2. Select a rectangle tool by clicking the icon.
3. Add rectangles.
4. Select rectangles to process. (`Alt` + `Ctrl` + `A`: Select all annotation objects `Ctrl` or `⌘` + left click: Select multiple objects)
5. Press the `Run for selected` button.
6. If you activate `Live mode`, SAM predicts a mask every time you add a rectangle.

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-rectangle-prompt.gif" width="768">

#### Point prompt

1. Select `Extensions` > `SAM` > `SAM prompt` from the menu bar.
2. Select a point tool by clicking the icon.
3. Add foreground points.
4. (Optional) add background points.
5. Press the `Run for selected` button.
6. If you activate `Live mode`, SAM predicts a mask every time you add a foreground point.

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-sam-point-prompt.gif" width="768">

#### Parameters

| key                  | value                                                                                                                                                                                                                                                                                                                                       |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Server               | URL of the server.                                                                                                                                                                                                                                                                                                                          |
| Output type          | If `Single Mask` is selected, the model will return single masks per prompt. If `Multi-mask` is selected, the model will return three masks per prompt. `Multi-mask (all)` keeps all three masks. One of the three masks is kept if the option `Multi-mask (largest)`, `Multi-mask (smallest)`, or `Multi-mask (best quality)` is selected. |
| Display names        | Display the annotation names in the viewer. (this is a global preference)                                                                                                                                                                                                                                                                   |
| Assign random colors | If checked and no path class is set in `Auto set` setting, assign random colors to new (unclassified) objects created by SAM.                                                                                                                                                                                                               |
| Assign names         | If checked, assign names to identify new objects as created by SAM, including quality scores.                                                                                                                                                                                                                                               |
| Keep prompts         | If checked, keep the foreground prompts after detection. If not checked, these are deleted.                                                                                                                                                                                                                                                 |
| Display names        | Display the annotation names in the viewer. (this is a global preference)                                                                                                                                                                                                                                                                   |

### SamAutomaticMaskGenerator

Select `Extensions` > `SAM` > `SAM auto mask` from the menu bar.

#### Parameters

| key                            | value                                                                                                                                                                                                                                                                                                                                       |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Server                         | URL of the server.                                                                                                                                                                                                                                                                                                                          |
| SAM model                      | One of `vit_h (huge)`, `vit_l (large)`, `vit_b (base)`, or `vit_t (mobile)`.                                                                                                                                                                                                                                                                |
| Assign random colors           | If checked and no path class is set in `Auto set` setting, assign random colors to new (unclassified) objects created by SAM.                                                                                                                                                                                                               |
| Assign names                   | If checked, assign names to identify new objects as created by SAM, including quality scores.                                                                                                                                                                                                                                               |
| Keep prompts                   | If checked, keep the foreground prompts after detection. If not checked, these are deleted.                                                                                                                                                                                                                                                 |
| Display names                  | Display the annotation names in the viewer. (this is a global preference)                                                                                                                                                                                                                                                                   |
| points_per_side                | The number of points to be sampled along one side of the image. The total number of points is points_per_side**2.                                                                                                                                                                                                                           |
| points_per_batch               | Sets the number of points run simultaneously by the model. Higher numbers may be faster but use more GPU memory.                                                                                                                                                                                                                            |
| pred_iou_thresh                | A filtering threshold in [0,1], using the model's predicted mask quality.                                                                                                                                                                                                                                                                   |
| stability_score_thresh         | A filtering threshold in [0,1], using the stability of the mask under changes to the cutoff used to binarize the model's mask predictions.                                                                                                                                                                                                  |
| stability_score_offset         | The amount to shift the cutoff when calculated the stability score.                                                                                                                                                                                                                                                                         |
| box_nms_thresh                 | The box IoU cutoff used by non-maximal suppression to filter duplicate masks.                                                                                                                                                                                                                                                               |
| crop_n_layers                  | If >0, mask prediction will be run again on crops of the image. Sets the number of layers to run, where each layer has 2**i_layer number of image crops.                                                                                                                                                                                    |
| crop_nms_thresh                | The box IoU cutoff used by non-maximal suppression to filter duplicate masks between different crops.                                                                                                                                                                                                                                       |
| crop_overlap_ratio             | Sets the degree to which crops overlap. In the first crop layer, crops will overlap by this fraction of the image length. Later layers with more crops scale down this overlap.                                                                                                                                                             |
| crop_n_points_downscale_factor | The number of points-per-side sampled in layer n is scaled down by crop_n_points_downscale_factor**n.                                                                                                                                                                                                                                       |
| min_mask_region_area           | If >0, postprocessing will be applied to remove disconnected regions and holes in masks with area smaller than min_mask_region_area. Requires opencv.                                                                                                                                                                                       |
| output_type                    | If 'Single Mask' is selected, the model will return single masks per prompt. If 'Multi-mask' is selected, the model will return three masks per prompt. 'Multi-mask (all)' keeps all three masks. One of the three masks is kept if the option 'Multi-mask (largest)', 'Multi-mask (smallest)', or 'Multi-mask (best quality)' is selected. |
| include_image_edge             | If True, include a crop area at the edge of the original image.                                                                                                                                                                                                                                                                             |

### Tips

If you select a class in `Auto set` in the Annotations tab, it is used for a new annotation generated by SAM.

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-extension-sam-class-auto-set.gif" width="768">

## Updates

### v0.3.0

#### Support for both point and rectangle foreground prompts
- Ensure each new point is a distinct object while SAM is running (i.e. turn of 'Multipoint' mode)
- Support line ROIs as a way of adding multiple points in a single object

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-multipoint.gif" width="768">

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-rectangle-prompt.gif" width="768">

#### Support point background prompts
- Points with 'ignored*' classifications are passed to the model as background prompts
  (Sidenote: it seems a large number of background points harm the prediction... or I've done something wrong)

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-multipoint-live.gif" width="768">

#### Implement 'Live mode' and 'Run for selected'
- 'Live mode' toggle button to turn live detection on or off
- Alternative 'Run for selected' button to use only the selected foreground and background objects
  - This makes it possible to annotate first, then run SAM across multiple objects - as required [on the forum](https://forum.image.sc/t/qupath-extension-segment-anything-model-sam/82420/10)

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-sam-point-prompt.gif" width="768">

#### Support SamAutomaticMaskGenerator

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-automask.gif" width="768">

#### Menu items simplified to a single command to launch a dialog to control annotation with SAM
- Provide persistent preferences for key choices (e.g. server, model)
- Run prediction in a background thread with (indeterminate) progress indicator
- Help the user with tooltips (and prompts shown at the bottom of the dialog)

#### Handle changing the current image while the command is running
- Send entire field of view for point prediction
  This is useful for one-click annotation of visible structures

#### Include the 'quality' metric as a measurement for objects that are created
Support z-stacks/time series (by using the image plane; there's no support for 3D objects)

#### Optionally assign names & random colors to identify the generated objects

#### Optionally return multiple (3) detections instead of 1
- Select which detection to retain based upon size or quality, or keep all of them

#### Optionally keep the prompt objects, instead of immediately deleting them

### v0.2.0

#### Support any number of channels

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-sam-5channels-1080p.gif" width="768">

## Citation

Please cite my paper on [bioRxiv](https://biorxiv.org/cgi/content/short/2023.06.13.544786v1).

```.bib
@article {Sugawara2023.06.13.544786,
	author = {Ko Sugawara},
	title = {Training deep learning models for cell image segmentation with sparse annotations},
	elocation-id = {2023.06.13.544786},
	year = {2023},
	doi = {10.1101/2023.06.13.544786},
	publisher = {Cold Spring Harbor Laboratory},
	abstract = {Deep learning is becoming more prominent in cell image analysis. However, collecting the annotated data required to train efficient deep-learning models remains a major obstacle. I demonstrate that functional performance can be achieved even with sparsely annotated data. Furthermore, I show that the selection of sparse cell annotations significantly impacts performance. I modified Cellpose and StarDist to enable training with sparsely annotated data and evaluated them in conjunction with ELEPHANT, a cell tracking algorithm that internally uses U-Net based cell segmentation. These results illustrate that sparse annotation is a generally effective strategy in deep learning-based cell image segmentation. Finally, I demonstrate that with the help of the Segment Anything Model (SAM), it is feasible to build an effective deep learning model of cell image segmentation from scratch just in a few minutes.Competing Interest StatementKS is employed part-time by LPIXEL Inc.},
	URL = {https://www.biorxiv.org/content/early/2023/06/13/2023.06.13.544786},
	eprint = {https://www.biorxiv.org/content/early/2023/06/13/2023.06.13.544786.full.pdf},
	journal = {bioRxiv}
}
```

## Acknowledgements

- [QuPath](https://qupath.github.io)
- [segment-anything](https://github.com/facebookresearch/segment-anything)
- [MobileSAM](https://github.com/ChaoningZhang/MobileSAM)