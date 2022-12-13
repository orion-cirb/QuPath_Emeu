# QuPath_Emeu

* **Developed for:** Coline
* **Team:** Manceau
* **Date:** September 2022
* **Software:** QuPath

### Images description

2D images of emu embryo sections taken with the Axioscan

2 channels: 
  1. *DAPI:* nuclei
  2. *EGFP:* cells

### Plugin description

* Divide each epidermis/dermis annotation provided in 10 segments of equal length
* Detect nuclei and cells in each segment with Stardist

### Dependencies

* **Stardist** QuPath extension + *dsb2018_heavy_augment.pb* model

### Version history

Version 1 (*detectNuclei.groovy*) released on September 9, 2022.
Version 2 (*detectNucleiFast.groovy*) released on December 13, 2022.
