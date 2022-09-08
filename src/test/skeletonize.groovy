import qupath.imagej.gui.ImageJMacroRunner
import qupath.lib.roi.ROIs
import qupathj.QuPath_Send_Overlay_to_QuPath

params = new ImageJMacroRunner(getQuPath()).getParameterList()

// Change the value of a parameter, using the JSON to identify the key
params.getParameters().get('downsampleFactor').setValue(1)
params.getParameters().get('sendROI').setValue(true)
params.getParameters().get('sendOverlay').setValue(false)
params.getParameters().get('getOverlay').setValue(false)
params.getParameters().get('getROI').setValue(false)
params.getParameters().get('clearObjects').setValue(false)

//new ImageJ macro
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

def imageData = getCurrentImageData()
def server = imageData.getServer()
def cal = server.getPixelCalibration()
    
def annotations = getAnnotationObjects()//.findAll{it.getPathClass().toString()=="Vessel"}
// Loop through the annotations and run the macro
for (annotation in annotations) {
    ImageJMacroRunner.runMacro(params, imageData, null, annotation, macro)
}

def skeleton = getAnnotationObjects().last()