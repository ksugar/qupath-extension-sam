# QuPath extension SAM

### New release v0.7: SAM2-based 2D+T tracking and 3D segmentation are supported now!
<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/sam2-sequence-demo.gif" width="768">

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-samapi.gif" width="768">

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-multipoint-live.gif" width="768">

This is a [QuPath](https://qupath.github.io) extension for [Segment Anything Model (SAM)](https://github.com/facebookresearch/segment-anything).

This is a part of the following paper. Please [cite](#citation) it when you use this project. You will also cite [the original SAM paper](https://arxiv.org/abs/2304.02643) and [the MobileSAM paper](https://arxiv.org/abs/2306.14289).

- Sugawara, K. [*Training deep learning models for cell image segmentation with sparse annotations.*](https://biorxiv.org/cgi/content/short/2023.06.13.544786v1) bioRxiv 2023. doi:10.1101/2023.06.13.544786

## Install

Drag and drop [the extension file](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.7.0/qupath-extension-sam-0.7.0.jar) to [QuPath](https://qupath.github.io) and restart it.

Since QuPath v0.5.0, you can install the extension from the extension manager dialog by specifying `Owner` and `Repository` as shown below.

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-extension-manager.png" width="768">

If you are using QuPath v0.4.x, you need to install [the extension file for QuPath v0.4.x](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.4.1/qupath-extension-sam-0.4.1.jar), which is now deprecated.

Please note that you need to set up the server following the instructions in the link below.

[https://github.com/ksugar/samapi](https://github.com/ksugar/samapi)

## Update

To update the `qupath-extension-sam`, follow the following instructions.

1. Open extensions directory from `Extensions` > `Installed extensions` > `Open extensions directory`
   ![](https://github.com/ksugar/qupath-extension-sam/releases/download/assets/open-extensions-directory.png)
2. Replace `qupath-extension-sam-x.y.z.jar` with [the latest version of the extension file](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.7.0/qupath-extension-sam-0.7.0.jar). If you are using QuPath v0.4.x, you need to install [the extension file for QuPath v0.4.x](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.4.1/qupath-extension-sam-0.4.1.jar), which is now deprecated.
3. Restart QuPath application.

Please note that you need to also update the [samapi](https://github.com/ksugar/samapi/tree/v0.5.0) server.  
To keep updated with the latest samapi server, follow the instructions [here](https://github.com/ksugar/samapi#update).

## Usage

### SAM prompt command

#### Rectangle (BBox) prompt

1. Select `Extensions` > `SAM` from the menu bar.
2. Select the `Prompt` tab in the `Segment Anyghing Model` dialog.
3. Select a rectangle tool by clicking the icon.
4. Add rectangles.
5. Select rectangles to process. (`Alt` + `Ctrl` + `A`: Select all annotation objects `Ctrl` or `⌘` + left click: Select multiple objects)
6. Press the `Run for selected` button.
7. If you activate `Live mode`, SAM predicts a mask every time you add a rectangle.

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-rectangle-prompt.gif" width="768">

#### Point prompt

1. Select `Extensions` > `SAM` from the menu bar.
2. Select the `Prompt` tab in the `Segment Anyghing Model` dialog.
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
| SAM type             | One of `vit_h (huge)`, `vit_l (large)`, `vit_b (base)`, `vit_t (mobile)`, `sam2_l (large)`, `sam2_bp (base plus)`, `sam2_s (small)`, or `sam2_t (tiny)`.                                                                                                                                                                                    |
| SAM weights          | The SAM weights to use. The options are automatically fetched from the server.                                                                                                                                                                                                                                                              |
| Output type          | If `Single Mask` is selected, the model will return single masks per prompt. If `Multi-mask` is selected, the model will return three masks per prompt. `Multi-mask (all)` keeps all three masks. One of the three masks is kept if the option `Multi-mask (largest)`, `Multi-mask (smallest)`, or `Multi-mask (best quality)` is selected. |
| Display names        | Display the annotation names in the viewer. (this is a global preference)                                                                                                                                                                                                                                                                   |
| Assign random colors | If checked and no path class is set in `Auto set` setting, assign random colors to new (unclassified) objects created by SAM.                                                                                                                                                                                                               |
| Assign names         | If checked, assign names to identify new objects as created by SAM, including quality scores.                                                                                                                                                                                                                                               |
| Keep prompts         | If checked, keep the foreground prompts after detection. If not checked, these are deleted.                                                                                                                                                                                                                                                 |
| Display names        | Display the annotation names in the viewer. (this is a global preference)                                                                                                                                                                                                                                                                   |

### SamAutomaticMaskGenerator

1. Select `Extensions` > `SAM` from the menu bar.
2. Select the `Auto mask` tab in the `Segment Anyghing Model` dialog.
3. Set parameters.
4. Press the `Run` button.

#### Parameters

| key                            | value                                                                                                                                                                                                                                                                                                                                       |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Server                         | URL of the server.                                                                                                                                                                                                                                                                                                                          |
| SAM type                       | One of `vit_h (huge)`, `vit_l (large)`, `vit_b (base)`, `vit_t (mobile)`, `sam2_l (large)`, `sam2_bp (base plus)`, `sam2_s (small)`, or `sam2_t (tiny)`.                                                                                                                                                                                    |
| SAM weights                    | The SAM weights to use. The options are automatically fetched from the server.                                                                                                                                                                                                                                                              |
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
### Register SAM weights from URL

1. Select `Extensions` > `SAM` from the menu bar.
2. Press the `Register` button in the `Segment Anyghing Model` dialog.

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-sam-register-weights-from-url.png" width="768">

The weights file is downloaded from the URL and registered on the server. After the registration, you can select the weights from the `SAM weights` dropdown menu.

#### Parameters

| key      | value                                                                                                                                                    |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| SAM type | One of `vit_h (huge)`, `vit_l (large)`, `vit_b (base)`, `vit_t (mobile)`, `sam2_l (large)`, `sam2_bp (base plus)`, `sam2_s (small)`, or `sam2_t (tiny)`. |
| Name     | The SAM weights name to register. It needs to be unique in the same SAM type.                                                                            |
| URL      | The URL to the SAM weights file.                                                                                                                         |

#### SAM weights catalog

Here is a list of SAM weights that you can register from the URL.

<table>
    <thead>
        <tr>
            <th>Type</th>
            <th>Name (customizable)</th>
            <th>URL</th>
            <th>Citation</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>vit_h</td>
            <td>vit_h_lm</td>
            <td><a href="https://zenodo.org/record/8250299/files/vit_h_lm.pth?download=1">https://zenodo.org/record/8250299/files/vit_h_lm.pth?download=1</a></td>
            <td rowspan="4">Archit, A. et al. <a href="https://doi.org/10.1101/2023.08.21.554208">Segment Anything for
                    Microscopy.</a> bioRxiv 2023. doi:10.1101/2023.08.21.554208<br><br><a href="https://github.com/computational-cell-analytics/micro-sam">https://github.com/computational-cell-analytics/micro-sam</a></td>
        </tr>
        <tr>
            <td>vit_b</td>
            <td>vit_b_lm</td>
            <td><a href="https://zenodo.org/record/8250281/files/vit_b_lm.pth?download=1">https://zenodo.org/record/8250281/files/vit_b_lm.pth?download=1</a></td>
        </tr>
        <tr>
            <td>vit_h</td>
            <td>vit_h_em</td>
            <td><a href="https://zenodo.org/record/8250291/files/vit_h_em.pth?download=1">https://zenodo.org/record/8250291/files/vit_h_em.pth?download=1</a></td>
        </tr>
        <tr>
            <td>vit_b</td>
            <td>vit_b_em</td>
            <td><a href="https://zenodo.org/record/8250260/files/vit_b_em.pth?download=1">https://zenodo.org/record/8250260/files/vit_b_em.pth?download=1</a></td>
        </tr>
    </tbody>
</table>

### Tips

If you select a class in `Auto set` in the Annotations tab, it is used for a new annotation generated by SAM.

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-extension-sam-class-auto-set.gif" width="768">

## Updates

### v0.7.0
- Support 2D+T tracking and 3D segmentation with [SAM2](https://ai.meta.com/sam2/) models, available with the [samapi](https://github.com/ksugar/samapi) server `v0.6.0` and above.

### v0.6.0
- Support [SAM2](https://ai.meta.com/sam2/) models, available with the [samapi](https://github.com/ksugar/samapi) server `v0.5.0` and above.
- Use the current view for the encoder input in the rectangle mode.

### v0.5.0
- QuPath v0.5 support by [@Rylern](https://github.com/Rylern) and [@ksugar](https://github.com/ksugar) in [ksugar/samapi#12](https://github.com/ksugar/qupath-extension-sam/pull/12)

### v0.4.1
- Properly send the checkpoint URL parameter
  - The checkpoint URL was not sent to the server.
- Add a catalog of SAM weights to README
- Add example scripts under [src/main/resources/scripts](src/main/resources/scripts)

### v0.4.0
- Support for registering SAM weights from URL. [ksugar/qupath-extension-sam#8](https://github.com/ksugar/qupath-extension-sam/issues/8) [ksugar/samapi#11](https://github.com/ksugar/samapi/pull/11) suggested and tested by [@constantinpape](https://github.com/constantinpape)
- Combine the `Prompt` and `Auto mask` dialogs into a single `Segment Anything Model` dialog.

### v0.3.0

- Support for both point and rectangle foreground prompts by [@petebankhead](https://github.com/petebankhead)
  - Ensure each new point is a distinct object while SAM is running (i.e. turn of 'Multipoint' mode)
  - Support line ROIs as a way of adding multiple points in a single object

  <img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-multipoint.gif" width="768">

  <img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-rectangle-prompt.gif" width="768">

- Support point background prompts by [@petebankhead](https://github.com/petebankhead)
  - Points with 'ignored*' classifications are passed to the model as background prompts
    (Sidenote: it seems a large number of background points harm the prediction... or I've done something wrong)

  <img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-multipoint-live.gif" width="768">

- Implement 'Live mode' and 'Run for selected' by [@petebankhead](https://github.com/petebankhead)
  - 'Live mode' toggle button to turn live detection on or off
  - Alternative 'Run for selected' button to use only the selected foreground and background objects
    - This makes it possible to annotate first, then run SAM across multiple objects - as required [on the forum](https://forum.image.sc/t/qupath-extension-segment-anything-model-sam/82420/10)

  <img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-sam-point-prompt.gif" width="768">

- Support SamAutomaticMaskGenerator

  <img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-automask.gif" width="768">

- Menu items simplified to a single command to launch a dialog to control annotation with SAM by [@petebankhead](https://github.com/petebankhead)
  - Provide persistent preferences for key choices (e.g. server, model)
  - Run prediction in a background thread with (indeterminate) progress indicator
  - Help the user with tooltips (and prompts shown at the bottom of the dialog)

- Handle changing the current image while the command is running by [@petebankhead](https://github.com/petebankhead)
  - Send entire field of view for point prediction
    This is useful for one-click annotation of visible structures

- Include the 'quality' metric as a measurement for objects that are created by [@petebankhead](https://github.com/petebankhead)

- Support z-stacks/time series (by using the image plane; there's no support for 3D objects) by [@petebankhead](https://github.com/petebankhead) and [@rharkes](https://github.com/rharkes)

- Optionally assign names & random colors to identify the generated objects by [@petebankhead](https://github.com/petebankhead)

- Optionally return multiple (3) detections instead of 1 by [@petebankhead](https://github.com/petebankhead)

- Select which detection to retain based upon size or quality, or keep all of them by [@petebankhead](https://github.com/petebankhead)

- Optionally keep the prompt objects, instead of immediately deleting them by [@petebankhead](https://github.com/petebankhead)

### v0.2.0

- Support any number of channels

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