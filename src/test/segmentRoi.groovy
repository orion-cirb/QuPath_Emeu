// File: tileAnnotation2.groovy

import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.viewer.QuPathViewer
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjects
import qupath.lib.roi.interfaces.ROI
import qupath.lib.roi.RoiTools


// Adjust THIS (n : Number of Tiles)
int n = 10
def trimToROI = true

QuPathViewer viewer = QuPathGUI.getInstance().getViewer()
def objSelected = viewer.getSelectedObject()

if (objSelected != null && objSelected instanceof PathAnnotationObject){
    ROI roi = ((PathAnnotationObject)objSelected).getROI()

    int w = roi.getBoundsWidth()
    int h = roi.getBoundsHeight()

    int tW, tH
    if ( w > h){
        tW = Math.floor(w/n)
        tH = h
    }
    else{
        tW = w
        tH = Math.floor(h/n)
    }

    List<ROI> pathROIs = RoiTools.makeTiles(roi, tW, tH, trimToROI)

    List<PathObject> tiles = new ArrayList<>(pathROIs.size())

    Iterator<ROI> iter = pathROIs.iterator()
    int idx = 0
    while (iter.hasNext()) {
        try {
            PathObject tile = PathObjects.createAnnotationObject(iter.next())
            if (tile != null) {
                idx++
                tile.setName("Tile " + idx)
                tiles.add(tile)
            }
        } catch (InterruptedException e) {
            lastMessage = "Tile creation interrupted for " + objSelected
            return
        } catch (Exception e) {
            iter.remove()
        }
    }

    ((PathAnnotationObject)objSelected).addPathObjects(tiles);
    viewer.getImageData().getHierarchy().fireHierarchyChangedEvent(this, objSelected);
}

println('Done!')
