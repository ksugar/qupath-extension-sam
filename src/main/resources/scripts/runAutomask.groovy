import org.elephant.sam.entities.SAMType;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.tasks.SAMAutoMaskTask;

def task = SAMAutoMaskTask.builder(getCurrentViewer())
                .serverURL("http://localhost:8000/sam/")
                .model(SAMType.VIT_L)
                .outputType(SAMOutput.MULTI_SMALLEST)
                .setName(true)
                .clearCurrentObjects(true)
                .setRandomColor(true)
                .pointsPerSide(16)
                .pointsPerBatch(64)
                .predIoUThresh(0.88)
                .stabilityScoreThresh(0.95)
                .stabilityScoreOffset(1.0)
                .boxNmsThresh(0.2)
                .cropNLayers(0)
                .cropNmsThresh(0.7)
                .cropOverlapRatio(512 / 1500)
                .cropNPointsDownscaleFactor(1)
                .minMaskRegionArea(0)
                .includeImageEdge(true)
                .build();
Platform.runLater(task);
