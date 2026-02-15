# QuPath extension SAM

### Latest release v0.9: Support for SAM3!

The extension now supports [SAM3](https://github.com/facebookresearch/segment-anything-3) models for enhanced segmentation and tracking capabilities.

<div><video controls src="https://github.com/ksugar/qupath-extension-sam/releases/download/v0.9.0/sam3_usage_CMU-1.mp4" muted="false"></video></div>


The command history is also now available from `Automate` > `Show workflow command history` and `Automate` > `Create command history script` in the menu bar.

#### Example scripts

<details><summary>SAMDetectionTask.groovy</summary>

```groovy
var foregroundObjects = [
    PathObjects.createAnnotationObject(
        ROIs.createRectangleROI(
            433.000000, 194.000000, 21.000000, 37.000000,
            new ImagePlane(-1, 0, 0)
        ),
        null
    ),
    PathObjects.createAnnotationObject(
        ROIs.createRectangleROI(
            416.000000, 174.000000, 23.000000, 32.000000,
            new ImagePlane(-1, 0, 0)
        ),
        null
    ),
]
var backgroundObjects = []
var task = org.elephant.sam.tasks.SAMDetectionTask.builder(getCurrentViewer())
    .server(org.elephant.sam.Utils.createRenderedServer(getCurrentViewer()))
    .regionRequest(RegionRequest.createInstance(getCurrentServer().getPath(), 0.934228, 0, 0, 696, 520, 0, 0))
    .serverURL("http://localhost:8000/sam/")
    .verifySSL(false)
    .model(org.elephant.sam.entities.SAMType.VIT_T)
    .outputType(org.elephant.sam.entities.SAMOutput.MULTI_SMALLEST)
    .setName(true)
    .setRandomColor(true)
    .checkpointUrl("https://github.com/ChaoningZhang/MobileSAM/raw/master/weights/mobile_sam.pt")
    .addForegroundPrompts(foregroundObjects)
    .addBackgroundPrompts(backgroundObjects)
    .build()
task.setOnSucceeded(event -> {
    List<PathObject> detected = task.getValue()
    if (detected != null) {
        if (!detected.isEmpty()) {
            Platform.runLater(() -> {
                PathObjectHierarchy hierarchy = getCurrentHierarchy()
                hierarchy.addObjects(detected)
                hierarchy.getSelectionModel().clearSelection()
                hierarchy.fireHierarchyChangedEvent(this)
            });
        } else {
            print("No objects detected")
        }
    }
});
Platform.runLater(task)
```
</details>

<details><summary>SAMAutoMaskTask.groovy</summary>

```groovy
var clearCurrentObjects = true
var task = org.elephant.sam.tasks.SAMAutoMaskTask.builder(getCurrentViewer())
    .server(org.elephant.sam.Utils.createRenderedServer(getCurrentViewer()))
    .regionRequest(RegionRequest.createInstance(getCurrentServer().getPath(), 0.934228, 0, 0, 696, 520, 0, 0))
    .serverURL("http://localhost:8000/sam/")
    .verifySSL(false)
    .model(org.elephant.sam.entities.SAMType.VIT_T)
    .outputType(org.elephant.sam.entities.SAMOutput.MULTI_SMALLEST)
    .setName(true)
    .clearCurrentObjects(clearCurrentObjects)
    .setRandomColor(true)
    .pointsPerSide(64)
    .pointsPerBatch(8)
    .predIoUThresh(0.880000)
    .stabilityScoreThresh(0.950000)
    .stabilityScoreOffset(1.000000)
    .boxNmsThresh(0.200000)
    .cropNLayers(0)
    .cropNmsThresh(0.700000)
    .cropOverlapRatio(0.340000)
    .cropNPointsDownscaleFactor(1)
    .minMaskRegionArea(0)
    .includeImageEdge(false)
    .checkpointUrl("https://github.com/ChaoningZhang/MobileSAM/raw/master/weights/mobile_sam.pt")
    .build()
task.setOnSucceeded(event -> {
    List<PathObject> detected = task.getValue()
    if (detected != null) {
        if (!detected.isEmpty()) {
            Platform.runLater(() -> {
                PathObjectHierarchy hierarchy = getCurrentHierarchy()
                if (clearCurrentObjects)
                    hierarchy.clearAll()
                hierarchy.addObjects(detected)
                hierarchy.getSelectionModel().clearSelection()
                hierarchy.fireHierarchyChangedEvent(this)
            });
        } else {
            print("No objects detected")
        }
    }
});
Platform.runLater(task)
```
</details>

<details><summary>SAMSequenceTask.groovy</summary>

```groovy
def fromIndex = 20
def toIndex = 40
var objs = [
    11: [org.elephant.sam.parameters.SAM2VideoPromptObject.builder(0).bbox(352, 142, 463, 287).build(),]
]
var indexToPathClass = [
    0: PathClass.getInstance("SAM0"),
]
var regionRequests = (fromIndex..toIndex).collect {RegionRequest.createInstance(getCurrentServer().getPath(), 0.687248, 0, 0, 512, 443, 0, it)}}
var task = org.elephant.sam.tasks.SAMSequenceTask.builder(getCurrentViewer())
    .server(org.elephant.sam.Utils.createRenderedServer(getCurrentViewer()))
    .regionRequests(regionRequests)
    .serverURL("http://localhost:8000/sam/")
    .verifySSL(false)
    .model(org.elephant.sam.entities.SAMType.SAM2_S)
    .promptMode(org.elephant.sam.entities.SAMPromptMode.XYT)
    .objs(objs)
    .checkpointUrl("https://dl.fbaipublicfiles.com/segment_anything_2/072824/sam2_hiera_small.pt")
    .indexOffset(fromIndex)
    .indexToPathClass(indexToPathClass)
    .planePosition(0)
    .build()
task.setOnSucceeded(event -> {
    List<PathObject> detected = task.getValue()
    if (detected != null) {
        if (!detected.isEmpty()) {
            Platform.runLater(() -> {
                PathObjectHierarchy hierarchy = getCurrentHierarchy()
                indexToPathClass.values().stream()
                        .filter(pathClass -> !getQuPath().getAvailablePathClasses().contains(pathClass))
                        .sorted(Comparator.comparing(PathClass::getName, new org.elephant.sam.comparators.NaturalOrderComparator()))
                        .forEachOrdered(pathClass -> getQuPath().getAvailablePathClasses().add(pathClass))
                hierarchy.addObjects(detected)
                hierarchy.getSelectionModel().clearSelection()
                hierarchy.fireHierarchyChangedEvent(this)
            });
        } else {
            print("No objects detected")
        }
    }
});
Platform.runLater(task)
```
</details>

### New release v0.7: SAM2-based 2D+T tracking and 3D segmentation are supported now!
<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/sam2-sequence-demo.gif" width="768">

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-samapi.gif" width="768">

<img src="https://github.com/ksugar/samapi/releases/download/assets/qupath-sam-multipoint-live.gif" width="768">

This is a [QuPath](https://qupath.github.io) extension for [Segment Anything Model (SAM)](https://github.com/facebookresearch/segment-anything).

This is a part of the following paper. Please [cite](#citation) it when you use this project. You will also cite [the original SAM paper](https://arxiv.org/abs/2304.02643) and [the MobileSAM paper](https://arxiv.org/abs/2306.14289).

- Sugawara, K. [*Training deep learning models for cell image segmentation with sparse annotations.*](https://biorxiv.org/cgi/content/short/2023.06.13.544786v1) bioRxiv 2023. doi:10.1101/2023.06.13.544786

## Install

Drag and drop [the extension file](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.9.1/qupath-extension-sam-0.9.1.jar) to [QuPath](https://qupath.github.io) and restart it.

Since QuPath v0.5.0, you can install the extension from the extension manager dialog by specifying `Owner` and `Repository` as shown below.

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-extension-manager.png" width="768">

If you are using QuPath v0.4.x, you need to install [the extension file for QuPath v0.4.x](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.4.1/qupath-extension-sam-0.4.1.jar), which is now deprecated.

Please note that you need to set up the server following the instructions in the link below.

[https://github.com/ksugar/samapi](https://github.com/ksugar/samapi)

## Update

To update the `qupath-extension-sam`, follow the following instructions.

1. Open extensions directory from `Extensions` > `Installed extensions` > `Open extensions directory`
   ![](https://github.com/ksugar/qupath-extension-sam/releases/download/assets/open-extensions-directory.png)
2. Replace `qupath-extension-sam-x.y.z.jar` with [the latest version of the extension file](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.9.1/qupath-extension-sam-0.9.1.jar). If you are using QuPath v0.4.x, you need to install [the extension file for QuPath v0.4.x](https://github.com/ksugar/qupath-extension-sam/releases/download/v0.4.1/qupath-extension-sam-0.4.1.jar), which is now deprecated.
3. Restart QuPath application.

Please note that you need to also update the [samapi](https://github.com/ksugar/samapi) server.  
To keep updated with the latest samapi server, follow the instructions [here](https://github.com/ksugar/samapi#update).

Starting from QuPath `v0.6`, you can receive notifications about new releases of the extension by adding the following settings.

1. Open QuPath
2. Go to Extensions → Manage extensions
3. Click Manage extension catalogs
4. Enter the catalog URL: https://github.com/ksugar/qupath-catalog-ksugar
5. Browse and install the extensions you need


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
2. Select the `Prompt` tab in the `Segment Anything Model` dialog.
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
2. Select the `Auto mask` tab in the `Segment Anything Model` dialog.
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
2. Press the `Register` button in the `Segment Anything Model` dialog.

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
	# MicroSAM models
        <tr>
            <td>vit_h</td>
            <td>vit_h_lm</td>
            <td><a href="https://zenodo.org/record/8250299/files/vit_h_lm.pth?download=1">https://zenodo.org/record/8250299/files/vit_h_lm.pth?download=1</a></td>
            <td rowspan="4">Archit, A. et al. <a href="https://doi.org/10.1038/s41592-024-02580-4">Segment Anything for
                    Microscopy.</a> Nature Methods 2025.<br><br><a href="https://github.com/computational-cell-analytics/micro-sam">https://github.com/computational-cell-analytics/micro-sam</a></td>
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
	# PathoSAM models
	<tr>
            <td>vit_h</td>
            <td>vit_h_histopathology</td>
            <td><a href="https://owncloud.gwdg.de/index.php/s/L7AcvVz7DoWJ2RZ/download">https://owncloud.gwdg.de/index.php/s/L7AcvVz7DoWJ2RZ/download</a></td>
            <td rowspan="2">Greibel, T. et al. <a href="https://doi.org/10.48550/arXiv.2502.00408">Segment Anything for
                    Histopathology.</a> arXiv 2025.<br><br><a href="https://github.com/computational-cell-analytics/patho-sam">https://github.com/computational-cell-analytics/patho-sam</a></td>
        </tr>
        <tr>
            <td>vit_b</td>
            <td>vit_b_histopathology</td>
            <td><a href="https://owncloud.gwdg.de/index.php/s/sBB4H8CTmIoBZsQ/download">https://owncloud.gwdg.de/index.php/s/sBB4H8CTmIoBZsQ/download</a></td>
        </tr>
    </tbody>
</table>

### Tips

If you select a class in `Auto set` in the Annotations tab, it is used for a new annotation generated by SAM.

<img src="https://github.com/ksugar/qupath-extension-sam/releases/download/assets/qupath-extension-sam-class-auto-set.gif" width="768">

### Known issues
- SAM3 video predictor does not work with negative bbox prompts. See https://github.com/facebookresearch/sam3/issues/335.

## Updates

### v0.9.1

- Enable the confidence threshold setting only for SAM3 models.
- Accept SAM3 requests without any text or bounding box prompts. This enables the use of SAM3 with a pre-existing inference state. The [samapi](https://github.com/ksugar/samapi) server version must be `v0.7.1` or above.
 
### v0.9.0
- Support SAM3 (you need to use the [samapi](https://github.com/ksugar/samapi) server `v0.7.0` or above).
- Accept server URL without a trailing slash.


### v0.8.0
- Support flexible Groovy scripts to run SAM tasks. See [the example scripts](#example-scripts).

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
