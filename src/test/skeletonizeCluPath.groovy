import ij.IJ
import qupath.imagej.tools.IJTools
import net.haesleinhuepf.clupath.CLUPATH

def findSkeleton(epidermis, server, request, downsample) {
    def pathImage = IJTools.convertToImagePlus(server, request)
    def imp = pathImage.getImage()
    def roi = IJTools.convertToIJRoi(epidermis.getROI(), pathImage)
    imp.setRoi(roi)
    def mask = imp.createRoiMask()
    imp.setProcessor(mask)

    def clijx = CLUPATH.getInstance()
    def imageIn = clijx.push(imp)
    def imageSkel = clijx.create(imageIn)

    // skeletonization
    clijx.skeletonize(imageIn, imageSkel)
    // pull back result and turn it into a QuPath ROI
    imp = clijx.pull(imageSkel)
    imp.show()

    def roiSkel = clijx.pullAsROI(imageSkel)
    imp.setRoi(roiSkel)
    imp.show()
    def imagePlane = IJTools.getImagePlane(roiSkel, imp)
    def roiQuPath = IJTools.convertToROI(roiSkel, -request.getX() / downsample, -request.getY() / downsample, downsample, imagePlane)
    // cleanup GPU memory
    clijx.clear()
    return (roiQuPath)
}