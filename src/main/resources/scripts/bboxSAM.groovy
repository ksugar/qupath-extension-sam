import org.elephant.sam.entities.SAMType;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.tasks.SAMDetectionTask;
import qupath.lib.roi.RectangleROI;

// Given that you have the following data in .csv
def data = [
    ['id', 'x', 'y', 'w', 'h'],
    ['0', '411', '84', '31', '33'],
    ['1', '413', '170', '32', '37']
]
def fileOut = File.createTempFile('bbox', '.csv');
fileOut.text = data*.join(',').join(System.lineSeparator());

// Read the data from the file
def fileIn = new File(fileOut.path);
def rows = fileIn.readLines().tail()*.split(',');
def plane = getCurrentViewer().getImagePlane();
def bboxes = rows.collect {
    PathObjects.createAnnotationObject(
        ROIs.createRectangleROI(
            it[1] as Double,
            it[2] as Double,
            it[3] as Double,
            it[4] as Double,
            plane
        )
    )
}

def server = getCurrentServer()
def path = server.getPath()

double downsample = 1.0
int x = 0
int y = 0
int width = 450
int height = 250

def regionRequest = RegionRequest.createInstance(path, downsample, x, y, width, height)
def task = SAMDetectionTask.builder(getCurrentViewer())
                .serverURL("http://localhost:8000/sam/")
                .regionRequest(regionRequest)
                .addForegroundPrompts(bboxes)
                .addBackgroundPrompts(Collections.emptyList())
                .model(SAMType.VIT_L)
                .outputType(SAMOutput.MULTI_SMALLEST)
                .setName(true)
                .setRandomColor(true)
                .build();
Platform.runLater(task);
def annotations = task.get();
addObjects(annotations);