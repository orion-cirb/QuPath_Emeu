import qupath.imagej.tools.PixelImageIJ
import qupath.lib.geom.Point2

// Compute points from binary mask
def pointsFromBinary(imp, regionRoi) {
    def ip = new PixelImageIJ(imp.getProcessor())
    def pixels = ip.getArray(true)
    def index = 0
    def points = []
    for (pix in pixels) {
        if (pix != 0) {
            def x = index % ip.getWidth() + regionRoi.getBoundsX()
            def y = (int)(index / ip.getWidth()) + regionRoi.getBoundsY()
            points << new Point2(x, y)
        }
        index++
    }
    return points
}

