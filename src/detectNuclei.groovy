// Imports
import org.apache.commons.io.FilenameUtils
import qupath.lib.gui.commands.Commands
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.roi.RoiTools
import qupath.lib.roi.ROIs
import qupath.lib.geom.Point2
import static qupath.lib.gui.scripting.QPEx.*
import qupath.ext.stardist.StarDist2D
import qupath.lib.objects.*
import qupath.lib.gui.dialogs.Dialogs
import qupath.imagej.gui.ImageJMacroRunner

// Init project
setImageType('Fluorescence')
def project = getProject()
def pathProject = buildFilePath(PROJECT_BASE_DIR)
def pathModel = buildFilePath(pathProject,'models','dsb2018_heavy_augment.pb')
if (pathModel == null) {
    Dialogs.showErrorMessage("Problem", 'No StarDist model found')
    return
}
def imageDir = new File(project.getImageList()[0].getUris()[0]).getParent()

// Set ImageJ parameters
params = new ImageJMacroRunner(getQuPath()).getParameterList()
params.getParameters().get('downsampleFactor').setValue(1)
params.getParameters().get('sendROI').setValue(true)
params.getParameters().get('sendOverlay').setValue(false)
params.getParameters().get('getOverlay').setValue(false)
params.getParameters().get('getROI').setValue(true)
params.getParameters().get('clearObjects').setValue(false)
// Define ImageJ macro
macro = """image = getTitle();
run("Create Mask");
run("Skeletonize (2D/3D)");
run("Analyze Particles...", "  show=[Overlay Masks] overlay add");
selectWindow(image);
roiManager("Add");
roiManager("Select", 0);
run("Send ROI to QuPath");
close();
close();
"""

// Create results file and write headers
def resultsDir = buildFilePath(imageDir, '/Results')
if (!fileExists(resultsDir)) mkdirs(resultsDir)
def resultsFile = new File(buildFilePath(resultsDir, 'Results.csv'))
resultsFile.createNewFile()
def resHeaders = 'Image name\tAnnotation name\tArea (um2)\tTile ID\tTile area (um2)\tNb DAPI cells\tNb GFP cells\n'
resultsFile.write(resHeaders)

// Define ClassPaths
def dapiCellsClass = PathClassFactory.getPathClass('DAPI', makeRGB(0,0,255))
def gfpCellsClass = PathClassFactory.getPathClass('GFP', makeRGB(0,255,0))

def findNearest(array, value) {
    def min = Double.MAX_VALUE
    for (val in array) {
        if (Math.abs(value - val) < Math.abs(value - min))
            min = val
    }
    return array.indexOf(min)
}

// Build StarDist model
def buildStarDistModel(pathModel, threshold, channel, cellClass) {
    return StarDist2D.builder(pathModel)
            .threshold(threshold)              // Prediction threshold
            .normalizePercentiles(1, 99)       // Percentile normalization
            .pixelSize(0.5)           // Resolution for detection
            .channels(channel)
            .constrainToParent(false)
            .measureShape()                  // Add shape measurements
            .measureIntensity()
            .classify(cellClass)
            .build()
}

// Detect cells in a specific annotation and channel
def detectCells(imageData, an, channel, pathModel, probThreshold, cellsClass, pixelWidth, minCellSize, minIntensity) {
    println '--- Finding ' + channel + ' cells in ROI ' + an.getName() + ' ---'
    def stardist = buildStarDistModel(pathModel, probThreshold, channel, cellsClass)
    stardist.detectObjects(imageData, an, true)
    def cells = getDetectionObjects().findAll{it.getPathClass() == cellsClass
            && it.getROI().getScaledArea(pixelWidth, pixelWidth) > minCellSize
            && it.getMeasurementList().getMeasurementValue(channel +': Median') > minIntensity
            && an.getROI().contains(it.getROI().getCentroidX(), it.getROI().getCentroidY())}
    println 'Nb cells detected = ' + cells.size() + ' (' + (getDetectionObjects().findAll{it.getPathClass() == cellsClass}.size() - cells.size()) + ' filtered out)'
    return cells
}

// Save annotations
def saveAnnotations(imgName) {
    def path = buildFilePath(imgName + '.annot')
    def annotations = getAnnotationObjects()
    new File(path).withObjectOutputStream {
        it.writeObject(annotations)
    }
    println('Annotations saved')
}

// Loop over images in project
def tools = new RoiTools()
for (entry in project.getImageList()) {
    def imageData = entry.readImageData()
    def server = imageData.getServer()
    def cal = server.getPixelCalibration()
    def pixelWidth = cal.getPixelWidth().doubleValue()
    def imgName = entry.getImageName()
    def imgNameWithOutExt = FilenameUtils.removeExtension(imgName)
    setBatchProjectAndImage(project, imageData)
    println ''
    println ''
    println '--------- ANALYZING IMAGE ' + imgName + ' ---------'

    // Find annotations
    def origin = getAnnotationObjects().find{it.getName() == "origin"}
    if (origin == null) {
        Dialogs.showErrorMessage("Problem", "Please create an 'origin' point in image " + imgName)
        continue
    }
    def epidermises = getAnnotationObjects().findAll{it.getName().contains("epidermis")}
    if (epidermises.isEmpty()) {
        Dialogs.showErrorMessage("Problem", "Please create 'epidermis' ROIs to analyze in image " + imgName)
        continue
    }
    def dermises = getAnnotationObjects().findAll{it.getName().contains("dermis")}

    for (epidermis in epidermises) {
        def dermis = dermises.find{it.getName().replace("dermis", "epidermis") == epidermis.getName()}

        println ''
        if (dermis != null) println '------ Analyzing ROIs ' + epidermis.getName() + ' and ' + dermis.getName() + ' ------'
        else println '------ Analyzing ROI ' + epidermis.getName() + ' ------'
        clearAllObjects()
        addObject(epidermis)
        addObject(origin)

        ImageJMacroRunner.runMacro(params, imageData, null, epidermis, macro)
        def skeleton = getAnnotationObjects().find{it.getROI().getRoiName() == "Geometry"}
        def skeletonSplitted = tools.splitROI(skeleton.getROI())
        println 'Skeleton computed'

        def skeletonPoints = []
        for (point in skeletonSplitted) {
            skeletonPoints << new Point2(point.getCentroidX(), point.getCentroidY())
        }
        skeletonPoints.sort{it.distance(new Point2(origin.getROI().getCentroidX(), origin.getROI().getCentroidY()))}

        def cumulativeLengths = []
        def length = 0
        for (def i = 0; i < skeletonPoints.size()-1; i++) {
            length += skeletonPoints[i].distance(skeletonPoints[i+1]) * pixelWidth
            cumulativeLengths << length
        }
        def skeletonLength = cumulativeLengths.last()
        println 'Skeleton length = ' + skeletonLength + ' um'

        def skeletonNormals = []
        for (def i = 0; i < 11; i++) {
            def landmark = null
            def p1 = null
            def p2 = null
            if (i == 0) {
                landmark = 0
                p1 = skeletonPoints[landmark]
                p2 = skeletonPoints[landmark+2]
            } else if (i == 10) {
                landmark = skeletonPoints.size()-1
                p1 = skeletonPoints[landmark]
                p2 = skeletonPoints[landmark-2]
            } else {
                landmark = findNearest(cumulativeLengths, i * 0.1 * skeletonLength)
                p1 = skeletonPoints[landmark-1]
                p2 = skeletonPoints[landmark+1]
            }

            def tangent = [p2.getX()-p1.getX(), p2.getY()-p1.getY()]
            def normal = [1, -tangent[0]/(tangent[1]+Double.MIN_VALUE)]
            def norm = Math.sqrt(Math.pow(normal[0], 2) + Math.pow(normal[1], 2))
            normal = [1000 * normal[0] / norm, 1000 * normal[1] / norm]
            skeletonNormals << [new Point2(p1.getX()+normal[0], p1.getY()+normal[1]), new Point2(p1.getX()-normal[0], p1.getY()-normal[1])]
        }

        def epidermis_tiles = []
        def dermis_tiles = []
        for (def i = 0; i < 10; i++) {
            def polygonPoints = null
            if (skeletonNormals[i][1].distance(skeletonNormals[i+1][1]) < skeletonNormals[i][1].distance(skeletonNormals[i+1][0])) {
                polygonPoints = [skeletonNormals[i][0], skeletonNormals[i][1], skeletonNormals[i+1][1], skeletonNormals[i+1][0]]
            } else {
                polygonPoints = [skeletonNormals[i][0], skeletonNormals[i][1], skeletonNormals[i+1][0], skeletonNormals[i+1][1]]
            }
            def polygon = ROIs.createPolygonROI(polygonPoints, epidermis.getROI().getImagePlane())

            def epidermis_tile = PathObjects.createAnnotationObject(tools.intersection([polygon, epidermis.getROI()]))
            epidermis_tile.setName("tile " + (i+1))
            epidermis_tiles << epidermis_tile

            if (dermis != null) {
                def dermis_tile = PathObjects.createAnnotationObject(tools.intersection([polygon, dermis.getROI()]))
                if (dermis_tile.getROI().getArea() > 0) {
                    dermis_tile.setName("tile " + (i+1))
                    dermis_tiles << dermis_tile
                }
            }
        }
        println 'Tiles created'

        // Detect cells in each channel
        def regions = [epidermis]
        def all_tiles = [epidermis_tiles]
        if (dermis != null) {
            regions << dermis
            all_tiles << dermis_tiles
        }
        for (int i = 0; i < regions.size(); i++) {
            def region = regions[i]
            def tiles = all_tiles[i]

            clearAllObjects()
            addObject(region)
            def dapiCells = detectCells(imageData, region, 'DAPI', pathModel, 0.6, dapiCellsClass,
                    pixelWidth, 0, 0)
            def gfpCells = detectCells(imageData, region, 'GFP', pathModel, 0.6, gfpCellsClass,
                    pixelWidth, 0, 0)
            region.clearPathObjects()
            for (tile in tiles) {
                def dapiChildren = dapiCells.findAll{tile.getROI().contains(it.getROI().getCentroidX(), it.getROI().getCentroidY())}
                tile.addPathObjects(dapiChildren)
                def gfpChildren = gfpCells.findAll{tile.getROI().contains(it.getROI().getCentroidX(), it.getROI().getCentroidY())}
                tile.addPathObjects(gfpChildren)

                // Save results
                def results = imgNameWithOutExt + '\t' + region.getName() + '\t' + region.getROI().getScaledArea(pixelWidth, pixelWidth) +
                        '\t' + tile.getName().replace('tile ', '') + '\t' + tile.getROI().getScaledArea(pixelWidth, pixelWidth) + '\t' + dapiChildren.size() + '\t' + gfpChildren.size()  + '\n'
                resultsFile << results
            }

            // Save detections
            deselectAll()
            selectObjects(region)
            runPlugin('qupath.lib.plugins.objects.DilateAnnotationPlugin', '{"radiusMicrons": 0.01,  "lineCap": "Round",  "removeInterior": false,  "constrainToParent": false}');
            region = getAnnotationObjects().last()
            region.addPathObjects(tiles)
            fireHierarchyUpdate()

            clearAllObjects()
            addObject(region)
            resolveHierarchy()
            saveAnnotations(buildFilePath(resultsDir, imgNameWithOutExt+"_"+region.getName()))
        }
        println ''
    }
    clearAllObjects()
    addObject(origin)
    saveAnnotations(buildFilePath(resultsDir, imgNameWithOutExt+"_origin"))
}
println 'Done!'