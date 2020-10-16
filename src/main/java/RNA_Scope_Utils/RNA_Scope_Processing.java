package RNA_Scope_Utils;


import static RNA_Scope.RNA_Scope.autoBackground;
import static RNA_Scope.RNA_Scope.cal;
import static RNA_Scope.RNA_Scope.deconv;
import static RNA_Scope.RNA_Scope.ghostDots;
import static RNA_Scope.RNA_Scope.pixDepth;
import static RNA_Scope.RNA_Scope.maxNucVol;
import static RNA_Scope.RNA_Scope.minNucVol;
import static RNA_Scope.RNA_Scope.nucDil;
import static RNA_Scope.RNA_Scope.output_detail_Analyze;
import static RNA_Scope.RNA_Scope.removeSlice;
import static RNA_Scope.RNA_Scope.roiBgSize;
import static RNA_Scope.RNA_Scope.singleDotIntGeneRef;
import static RNA_Scope.RNA_Scope.singleDotIntGeneX;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.GaussianBlur3D;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import mcib3d.geom.Object3D;
import mcib3d.geom.Object3DVoxels;
import mcib3d.geom.Object3D_IJUtils;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
import mcib3d.geom.Voxel3D;
import mcib3d.image3d.ImageFloat;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import mcib3d.image3d.distanceMap3d.EDT;
import mcib3d.image3d.processing.FastFilters3D;
import mcib3d.image3d.regionGrowing.Watershed3D;
import mpicbg.ij.integral.RemoveOutliers;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;



/**
 *
 * @author phm
 */

public class RNA_Scope_Processing {
    
    public static CLIJ2 clij2 = CLIJ2.getInstance();

    
    /**
     * Dialog ask for channels order
     * @param channels
     * @param showCal
     * @param cal
     * @return ch

     */
    public static ArrayList dialog(String[] channels, boolean showCal, Calibration cal) {
        ArrayList ch = new ArrayList();
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("DAPI           : ", channels, channels[0]);
        gd.addChoice("Gene Reference : ", channels, channels[1]);
        gd.addChoice("Gene X         : ", channels, channels[2]);
        gd.addMessage("nucleus volume filter parameters", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min Volume size : ", minNucVol, 2);
        gd.addNumericField("Max Volume size : ", maxNucVol, 2);
        gd.addNumericField("Nucleus dilatation : ", nucDil, 2);
        gd.addNumericField("Section to remove  : ",removeSlice, 0);
        gd.addMessage("Single dot calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Gene reference single dot mean intensity : ", singleDotIntGeneRef, 0);
        gd.addNumericField("Gene X single dot mean intensity : ", singleDotIntGeneX, 0);
        gd.addMessage("Auto background", Font.getFont("Monospace"), Color.blue);
        gd.addCheckbox("Auto background ", autoBackground);
        gd.addNumericField("Size of background box size : ", roiBgSize, 0);
        gd.addCheckbox("Remove ghost dots ", ghostDots);
        if (showCal) {
            gd.addMessage("No Z step calibration found", Font.getFont("Monospace"), Color.red);
            gd.addNumericField("XY pixel size : ", cal.pixelWidth, 3);
            gd.addNumericField("Z pixel size : ", pixDepth, 3);
        }
        gd.showDialog();
        ch.add(0, gd.getNextChoice());
        ch.add(1, gd.getNextChoice());
        ch.add(2, gd.getNextChoice());
        minNucVol = gd.getNextNumber();
        maxNucVol = gd.getNextNumber();
        nucDil = (float)gd.getNextNumber();
        removeSlice = (int)gd.getNextNumber();
        singleDotIntGeneRef = gd.getNextNumber();
        singleDotIntGeneX = gd.getNextNumber();
        autoBackground = gd.getNextBoolean();
        roiBgSize = (int)gd.getNextNumber();
        ghostDots = gd.getNextBoolean();
        if (showCal) {
            cal.pixelWidth = gd.getNextNumber();
            cal.pixelDepth = gd.getNextNumber();
        }
        
        if(gd.wasCanceled())
            ch = null;
        return(ch);
    }
    
    /**
     *
     * @param img
     */
    public static void closeImages(ImagePlus img) {
        img.flush();
        img.close();
    }
    
  /**
     * return objects population in an binary image
     * Using CLIJ2
     * @param imgCL
     * @return pop
     */

    public static Objects3DPopulation getPopFromClearBuffer(ClearCLBuffer imgCL) {
        ClearCLBuffer output = clij2.create(imgCL);
        clij2.connectedComponentsLabelingBox(imgCL, output);
        ImagePlus imgLab  = clij2.pull(output);
        imgLab.setCalibration(cal);
        ImageInt labels = new ImageLabeller().getLabels(ImageHandler.wrap(imgLab));
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        clij2.release(output);
        return pop;
    }  
    
    /**
     * gaussian 3D filter 
     * Using CLIJ2
     * @param imgCL
     * @param sizeX
     * @param sizeY
     * @param sizeZ
     * @return imgOut
     */
 
    public static ClearCLBuffer gaussianBlur3D(ClearCLBuffer imgCL, double sizeX, double sizeY, double sizeZ) {
        ClearCLBuffer imgOut = clij2.create(imgCL);
        clij2.gaussianBlur3D(imgCL, imgOut, sizeX, sizeY, sizeZ);
        clij2.release(imgCL);
        return(imgOut);
    }
    
     /**  
     * median 3D box filter
     * Using CLIJ2
     * @param imgCL
     * @param sizeX
     * @param sizeY
     * @param sizeZ
     * @return imgOut
     */ 
    public static ClearCLBuffer medianFilter(ClearCLBuffer imgCL, double sizeX, double sizeY, double sizeZ) {
        ClearCLBuffer imgIn = clij2.push(imgCL);
        ClearCLBuffer imgOut = clij2.create(imgIn);
        clij2.median3DBox(imgIn, imgOut, sizeX, sizeY, sizeZ);
        clij2.release(imgCL);
        return(imgOut);
    }
    
    /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param imgCL
     * @param sizeX1
     * @param sizeY1
     * @param sizeZ1
     * @param sizeX2
     * @param sizeY2
     * @param sizeZ2
     * @return imgGauss
     */ 
    public static ClearCLBuffer DOG(ClearCLBuffer imgCL, double sizeX1, double sizeY1, double sizeZ1, double sizeX2, double sizeY2, double sizeZ2) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, sizeX1, sizeY1, sizeZ1, sizeX2, sizeY2, sizeZ2);
        clij2.release(imgCL);
        return(imgCLDOG);
    }
    
    /**
     * Fill hole
     * USING CLIJ2
     */
    private static void fillHole(ClearCLBuffer imgCL) {
        long[] dims = clij2.getDimensions(imgCL);
        ClearCLBuffer slice = clij2.create(dims[0], dims[1]);
        ClearCLBuffer slice_filled = clij2.create(slice);
        for (int z = 0; z < dims[2]; z++) {
            clij2.copySlice(imgCL, slice, z);
            clij2.binaryFillHoles(slice, slice_filled);
            clij2.copySlice(slice_filled, imgCL, z);
        }
        clij2.release(slice);
        clij2.release(slice_filled);
    }
    
  /**
   * Open
   * USING CLIJ2
   * @param imgCL
   * @return imgCLOut
   */
    private static ClearCLBuffer open(ClearCLBuffer imgCL) {
        ClearCLBuffer imgCLOut = clij2.create(imgCL);
        clij2.openingBox(imgCL, imgCLOut, 1);
        clij2.release(imgCL);
        return(imgCLOut);
    }
    
    /**
     * Threshold 
     * USING CLIJ2
     * @param imgCL
     * @param thMed
     * @param fill 
     */
    public static ClearCLBuffer threshold(ClearCLBuffer imgCL, String thMed, boolean fill) {
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        if (fill)
            fillHole(imgCLBin);
        return(imgCLBin);
    }
    

    /**
     * Find gene population
     * @param imgGeneRef
     * @return genePop
     */
    public static Objects3DPopulation findGenePop(ImagePlus imgGeneRef) {
        ImagePlus img = new Duplicator().run(imgGeneRef);
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLMed = medianFilter(imgCL, 1, 1, 1);
        clij2.release(imgCL);
        ClearCLBuffer imgCLBin;
        if (!deconv) {
            ClearCLBuffer imgCLDOG = DOG(imgCLMed, 1, 1, 1, 2, 2, 2);
            clij2.release(imgCLMed);
            imgCLBin = threshold(imgCLDOG, "IsoData", false); 
            clij2.release(imgCLDOG);
        }
        else {
            imgCLBin = threshold(imgCLMed, "IsoData", false); 
        }
        Objects3DPopulation genePop = new Objects3DPopulation();
        if (ghostDots)
            genePop = getPopFromClearBuffer(open(imgCLBin)); 
        else
            genePop = getPopFromClearBuffer(imgCLBin);
        clij2.release(imgCLBin);       
        return(genePop);
    }
    
    
    /**
     * ramdom color nucleus population
     */
    public static ImagePlus colorPop (Objects3DPopulation cellsPop,  ImagePlus img) {
        //create image objects population
        Font tagFont = new Font("SansSerif", Font.PLAIN, 30);
        ImageHandler imgObj = ImageInt.wrap(img).createSameDimensions();
        imgObj.setCalibration(img.getCalibration());
        for (int i = 0; i < cellsPop.getNbObjects(); i++) {
            int color = (int)(Math.random() * (255 - 1 + 1) + 1);
            Object3D obj = cellsPop.getObject(i);
            obj.draw(imgObj, color);
            String name = Integer.toString(i+1);
            int[] box = obj.getBoundingBox();
            int z = (int)obj.getCenterZ();
            int x = box[0] - 2;
            int y = box[2] - 2;
            imgObj.getImagePlus().setSlice(z+1);
            ImageProcessor ip = imgObj.getImagePlus().getProcessor();
            ip.setFont(tagFont);
            ip.setColor(color);
            ip.drawString(name, x, y);
            imgObj.getImagePlus().updateAndDraw();
        } 
        return(imgObj.getImagePlus());
    } 
    
    /**
     * Tags cell with gene spot, Integrated intensity and max spot Integrated intensty ....
     * @param cellsPop (nucleus dilated population)
     * @param dotsPop gene population
     * @param imgGeneRef
     * @param imgGeneX
     */
    
    public static ArrayList<Cell> tagsCells(Objects3DPopulation cellsPop, Objects3DPopulation dotsRefPop, Objects3DPopulation dotsXPop, ImagePlus imgGeneRef,
            ImagePlus imgGeneX, Roi roiBgGeneRef, Roi roiBgGeneX) {
        
        IJ.showStatus("Finding cells with gene reference ...");
        ArrayList<Cell> cells = new ArrayList<>();
        ImageHandler imhRef = ImageHandler.wrap(imgGeneRef);
        ImageHandler imhX = ImageHandler.wrap(imgGeneX);
        int index = 0;
        
        // crop image for background
        imgGeneRef.setRoi(roiBgGeneRef);
        ImagePlus imgGeneRefCrop = imgGeneRef.crop("stack");
        imgGeneX.setRoi(roiBgGeneX);
        ImagePlus imgGeneXCrop = imgGeneX.crop("stack");
        
        for (int i = 0; i < cellsPop.getNbObjects(); i++) {
            double geneRefDotsVol = 0, geneXDotsVol = 0;
            double geneRefDotsInt = 0, geneXDotsInt = 0;
            
            // calculate cell parameters
            index++;
            Object3D cellObj = cellsPop.getObject(i);
            double zCell = cellObj.getCenterZ();
            double cellVol = cellObj.getVolumePixels();
            double cellGeneRefInt = cellObj.getIntegratedDensity(imhRef);
            double cellGeneXInt = cellObj.getIntegratedDensity(imhX);
            
            int cellMinZ = cellObj.getZmin() == 0 ? 1 : cellObj.getZmin();
            int cellMaxZ = cellObj.getZmax() > imgGeneRef.getNSlices() ? imgGeneRef.getNSlices() : cellObj.getZmax();
            
            
            // Cell background
            double bgGeneRef = find_background(imgGeneRefCrop, cellMinZ, cellMaxZ);
            double bgGeneX = find_background(imgGeneXCrop, cellMinZ, cellMaxZ);
            //System.out.println("Mean Background  ref = " + bgGeneRef + " zmin "+cellMinZ+" zmax "+cellMaxZ);
            //System.out.println("Mean Background  X = " + bgGeneX);
            
            // ref dots parameters
            for (int n = 0; n < dotsRefPop.getNbObjects(); n++) {
                Object3D dotObj = dotsRefPop.getObject(n);
                // find dots inside cell
                if (dotObj.hasOneVoxelColoc(cellObj)) {
                    geneRefDotsVol += dotObj.getVolumePixels();
                    geneRefDotsInt += dotObj.getIntegratedDensity(imhRef);
                }
            }
            
            // X dots parameters
            for (int n = 0; n < dotsXPop.getNbObjects(); n++) {
                Object3D dotObj = dotsXPop.getObject(n);
                // find dots inside cell
                if (dotObj.hasOneVoxelColoc(cellObj)) {
                    geneXDotsVol += dotObj.getVolumePixels();
                    geneXDotsInt += dotObj.getIntegratedDensity(imhX);
                }
            }
            // dots number based on cell intensity
            int nbGeneRefDotsCellInt = Math.round((float)((cellGeneRefInt - bgGeneRef * cellVol) / singleDotIntGeneRef));
            int nbGeneXDotsCellInt = Math.round((float)((cellGeneXInt - bgGeneX * cellVol) / singleDotIntGeneX));
            
            // dots number based on dots segmented intensity
            int nbGeneRefDotsSegInt = Math.round((float)((geneRefDotsInt - bgGeneRef * geneRefDotsVol) / singleDotIntGeneRef));
            int nbGeneXDotsSegInt = Math.round((float)((geneXDotsInt - bgGeneX * geneXDotsVol) / singleDotIntGeneX));
            
            Cell cell = new Cell(index, cellVol, zCell, cellGeneRefInt, bgGeneRef, geneRefDotsVol, geneRefDotsInt, nbGeneRefDotsCellInt, nbGeneRefDotsSegInt, cellGeneXInt,
                    bgGeneX, geneXDotsVol, geneXDotsInt, nbGeneXDotsCellInt, nbGeneXDotsSegInt);
            cells.add(cell);
        }
        closeImages(imgGeneRefCrop);
        closeImages(imgGeneXCrop);
        return(cells);
    }
    
    /**
     * Return dilated object restriced to image borders
     * @param img
     * @param obj
     * @return 
     */
    private static Object3DVoxels dilCellObj(ImagePlus img, Object3D obj) {
        Object3D objDil = obj.getDilatedObject((float)(nucDil/cal.pixelWidth), (float)(nucDil/cal.pixelHeight), (float)(nucDil));
        // check if object go outside image
        if (objDil.getXmin() < 0 || objDil.getXmax() > img.getWidth() || objDil.getYmin() < 0 || objDil.getYmax() > img.getHeight()
                || objDil.getZmin() < 0 || objDil.getZmax() > img.getNSlices()) {
            Object3DVoxels voxObj = new Object3DVoxels(objDil.listVoxels(ImageHandler.wrap(img)));
            return(voxObj);
        }
        else
            return(objDil.getObject3DVoxels());
    }
    
    private static Objects3DPopulation findCell(ImagePlus img, Objects3DPopulation nucPop) {
        
        ArrayList<Point3D> nucCenter = new ArrayList<>();
        GaussianBlur3D.blur(img, 6, 6, 6);
        // get nucleus center positions 
        for (int n = 0; n < nucPop.getNbObjects(); n++) {
            Object3D obj = nucPop.getObject(n);
            nucCenter.add(obj.getCenterAsPoint());
        }
        // and add pointRoi to imgGene
        PointRoi ptRoi = new PointRoi();
        for (Point3D pt : nucCenter) {
            img.setSlice(pt.getRoundZ());
            ptRoi.addPoint(img, pt.getRoundX(), pt.getRoundY());
        }
        img.setRoi(ptRoi);
        IJ.run(img, "Cell Outliner", "cell_radius=70 tolerance=0.9 kernel_width=5 dark_edge kernel_smoothing=1 polygon_smoothing=1 weighting_gamma=3 iterations=3 dilate=0 all_slices");
        ImagePlus cellOutline = WindowManager.getImage(img.getTitle() + " Cell Outline");
        if (cellOutline.isVisible()) {
            cellOutline.hide();
            cellOutline.deleteRoi();
        }
        cellOutline.setCalibration(img.getCalibration());
        Objects3DPopulation cellsPop = new Objects3DPopulation(cellOutline);
        closeImages(cellOutline);
        return(cellsPop);
    }
    
    
    public static  Objects3DPopulation findNucleus(ImagePlus imgNuc, ImagePlus imgGene) {
        Objects3DPopulation nucPopOrg = new Objects3DPopulation();
        nucPopOrg = find_nucleus2(imgNuc);
        System.out.println("-- Total nucleus Population :"+nucPopOrg.getNbObjects());
        // size filter
        Objects3DPopulation nucPop = new Objects3DPopulation(nucPopOrg.getObjectsWithinVolume(minNucVol, maxNucVol, true));
        int nbNucPop = nucPop.getNbObjects();
        System.out.println("-- Total nucleus Population after size filter: "+ nbNucPop);
        // create dilated nucleus population
        Objects3DPopulation cellsPop = new Objects3DPopulation();
        if (nucDil != 0)
            for (int o = 0; o < nucPop.getNbObjects(); o++) {
                Object3D obj = nucPop.getObject(o);
                //cellsPop.addObject(obj.getDilatedObject((float)(nucDil/cal.pixelWidth), (float)(nucDil/cal.pixelHeight), (float)(nucDil)));
                cellsPop.addObject(dilCellObj(imgNuc, obj));
            }
        else
            cellsPop = findCell(imgGene, nucPop);
        return(cellsPop);
    }
    
    /**
     * Remove Outliers
     * 
     * @param img
     * @param radX
     * @param radY
     * @param factor
     * @return img
     */
    public static ImagePlus removeOutliers(ImagePlus img, int radX, int radY, float factor) {
        
        for (int i = 0; i < img.getNSlices(); i++) {
            img.setSlice(i);
            ImageProcessor ip = img.getProcessor();
            RemoveOutliers removeOut = new RemoveOutliers(ip.convertToFloatProcessor());
            removeOut.removeOutliers(radX, radY, factor);
        }
        return(img);
    } 
    
    
    /**
     * Nucleus segmentation 2
     * @param imgNuc
     * @return cellPop
     */
    public static Objects3DPopulation find_nucleus2(ImagePlus imgNuc) {
        ImagePlus img = new Duplicator().run(imgNuc);
        ImageStack stack = new ImageStack(img.getWidth(), imgNuc.getHeight());
        for (int i = 1; i <= img.getStackSize(); i++) {
            IJ.showStatus("Finding nucleus section "+i+" / "+img.getStackSize());
            img.setZ(i);
            img.updateAndDraw();
            IJ.run(img, "Nuclei Outline", "blur=20 blur2=30 threshold_method=Triangle outlier_radius=15 outlier_threshold=1 max_nucleus_size=500 "
                    + "min_nucleus_size=10 erosion=5 expansion_inner=5 expansion=5 results_overlay");
            img.setZ(1);
            img.updateAndDraw();
            ImagePlus mask = new ImagePlus("mask", img.createRoiMask().getBufferedImage());
            ImageProcessor ip =  mask.getProcessor();
            ip.invertLut();
            for (int n = 0; n < 3; n++) 
                ip.erode();
            stack.addSlice(ip);
        }
        ImagePlus imgStack = new ImagePlus("Nucleus", stack);
        IJ.showStatus("Starting watershed...");
        ImagePlus imgWater = WatershedSplit(imgStack, 8);
        closeImages(imgStack);
        imgWater.setCalibration(imgNuc.getCalibration());
        Objects3DPopulation cellPop = new Objects3DPopulation(imgWater);
        cellPop.removeObjectsTouchingBorders(imgWater, false);
        closeImages(imgWater);
        closeImages(img);
        return(cellPop);
    }
    

    
    
    private static ImagePlus WatershedSplit(ImagePlus binaryMask, float rad) {
        float resXY = 1;
        float resZ = 1;
        float radXY = rad;
        float radZ = rad;
        Calibration cal = binaryMask.getCalibration();
        if (cal != null) {
            resXY = (float) cal.pixelWidth;
            resZ = (float) cal.pixelDepth;
            radZ = radXY * (resXY / resZ);
        }
        ImageInt imgMask = ImageInt.wrap(binaryMask);
        ImageFloat edt = EDT.run(imgMask, 0, resXY, resZ, false, 0);
        ImageHandler edt16 = edt.convertToShort(true);
        ImagePlus edt16Plus = edt16.getImagePlus();
        GaussianBlur3D.blur(edt16Plus, 2.0, 2.0, 2.0);
        edt16 = ImageInt.wrap(edt16Plus);
        edt16.intersectMask(imgMask);
        // seeds
        ImageHandler seedsImg = FastFilters3D.filterImage(edt16, FastFilters3D.MAXLOCAL, radXY, radXY, radZ, 0, false);
        Watershed3D water = new Watershed3D(edt16, seedsImg, 0, 0);
        water.setLabelSeeds(true);
        return(water.getWatershedImage3D().getImagePlus());
    }
    
    /**
     * Find min background roi
     * @param imgGene
     * @param size
     * @return 
     */
    public static Roi findRoiBbackgroundAuto(ImagePlus imgGeneProj, int size) {
        // measure min intensity in gene Z projection image 
        // take min intensity of rois found
        Roi roiBg = null;
        ArrayList<Double> intBgFound = new ArrayList();
        ArrayList<Roi> bgRoiFound = new ArrayList();
        int bgCount = 0;
        for (int x = 0; x < imgGeneProj.getWidth() - size; x += size) {
            for (int y = 0; y < imgGeneProj.getHeight() - size; y += size) {
                Roi roi = new Roi(x, y, size, size);
                imgGeneProj.setRoi(roi);
                ImageProcessor ip = imgGeneProj.getProcessor();
                ImageStatistics statsGenes = ip.getStats();
                intBgFound.add(statsGenes.mean);
                bgRoiFound.add(roi);
                bgCount++;
            }
        }
        int minIndex;
        minIndex = intBgFound.indexOf(Collections.min(intBgFound));
        roiBg = bgRoiFound.get(minIndex);
        System.out.println(bgCount+" bg box found size = " + size+ " bg min = "+ intBgFound.get(minIndex));
        return(roiBg);
    }
    
    
    /*
    * Get Mean of intensity in stack
    */
    public static double find_background(ImagePlus img, int zMin, int zMax) {
        ResultsTable rt = new ResultsTable();
        Analyzer ana = new Analyzer(img, Measurements.INTEGRATED_DENSITY, rt);
        double intDen = 0;
        int index = 0;
        for (int z = zMin; z <= zMax; z++) {
            img.setSlice(z);
            ana.measure();
            intDen += rt.getValue("RawIntDen", index);
            index++;
        }
        double vol = img.getWidth() * img.getHeight() * (zMax - zMin + 1);
        double bgInt = intDen / vol;
        rt.reset();
        return(bgInt);  
    }
    
    
    /**
     * Label object
     * @param popObj
     * @param img 
     */
    public static void labelsObject (Objects3DPopulation popObj, ImagePlus img, int fontSize) {
        Font tagFont = new Font("SansSerif", Font.PLAIN, fontSize);
        String name;
        for (int n = 0; n < popObj.getNbObjects(); n++) {
            Object3D obj = popObj.getObject(n);
            name = Integer.toString(n+1);
            int[] box = obj.getBoundingBox();
            int z = (int)obj.getCenterZ();
            int x = box[0] - 2;
            int y = box[2] - 2;
            img.setSlice(z+1);
            ImageProcessor ip = img.getProcessor();
            ip.setFont(tagFont);
            ip.setColor(255);
            ip.drawString(name, x, y);
            img.updateAndDraw();
        }
    }
    
    /**
     * 
     * @param xmlFile
     * @return
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException 
     */
    public static ArrayList<Point3D> readXML(String xmlFile) throws ParserConfigurationException, SAXException, IOException {
        ArrayList<Point3D> ptList = new ArrayList<>();
        double x = 0, y = 0 ,z = 0;
        File fXmlFile = new File(xmlFile);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	Document doc = dBuilder.parse(fXmlFile);
        doc.getDocumentElement().normalize();
        NodeList nList = doc.getElementsByTagName("Marker");
        for (int n = 0; n < nList.getLength(); n++) {
            Node nNode = nList.item(n);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                x = Double.parseDouble(eElement.getElementsByTagName("MarkerX").item(0).getTextContent());
                y = Double.parseDouble(eElement.getElementsByTagName("MarkerY").item(0).getTextContent());
                z = Double.parseDouble(eElement.getElementsByTagName("MarkerZ").item(0).getTextContent());
            }
            Point3D pt = new Point3D(x, y, z);
            ptList.add(pt);
        }
        return(ptList);
    }
    
    public static void InitResults(String outDirResults) throws IOException {
        // initialize results files
        // Detailed results
        FileWriter  fwAnalyze_detail = new FileWriter(outDirResults + "detailed_results.xls",false);
        output_detail_Analyze = new BufferedWriter(fwAnalyze_detail);
        // write results headers
        output_detail_Analyze.write("Image Name\t#Cell\tCell Vol (pixel3)\tCell Z center\tCell Integrated intensity in gene ref. channel\tMean background intensity in ref. channel\t"
                + "Total dots gene ref. (based on cell intensity)\tDots ref. volume (pixel3)\tIntegrated intensity of dots ref. channel\t"
                + "Total dots gene ref (based on dots seg intensity)\tCell Integrated intensity in gene X channel\tMean background intensity in X channel\t"
                + "Total dots gene X (based on cell intensity)\tDots X volume (pixel3)\tIntegrated intensity of dots X channel\tTotal dots gene X (based on dots seg intensity)\n");
        output_detail_Analyze.flush();
    }
    
    /**
     * Save nucleus with random colors
     * @param imgNuc
     * @param cellsPop
     * @param outDirResults
     * @param rootName
     */
    public static void saveCells (ImagePlus imgNuc, Objects3DPopulation cellsPop, String outDirResults, String rootName) {
        ImagePlus imgColorPop = colorPop (cellsPop, imgNuc);
        IJ.run(imgColorPop, "3-3-2 RGB", "");
        FileSaver ImgColorObjectsFile = new FileSaver(imgColorPop);
        ImgColorObjectsFile.saveAsTiff(outDirResults + rootName + "_Cells-ColorObjects.tif");
        closeImages(imgColorPop);
    }
    
    
    /**
     * save images objects population
     * @param imgNuc
     * @param cellsPop
     * @param imgGeneRef
     * @param imgGeneX
     * @param outDirResults
     * @param rootName
     */
    public static void saveCellsLabelledImage (ImagePlus imgNuc, Objects3DPopulation cellsPop, ImagePlus imgGeneRef, ImagePlus imgGeneX,
            String outDirResults, String rootName) {
        // red geneRef , green geneX, blue nucDilpop
        ImageHandler imgCells = ImageHandler.wrap(imgNuc).createSameDimensions();
        ImagePlus imgCellLabels = ImageHandler.wrap(imgNuc).createSameDimensions().getImagePlus();
        // draw nucleus population
        cellsPop.draw(imgCells, 255);
        labelsObject(cellsPop, imgCellLabels, 24);
        ImagePlus[] imgColors = {imgGeneRef, imgGeneX, imgCells.getImagePlus(), null, null, null, imgCellLabels};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_Objects.tif");
        imgCells.closeImagePlus();
        closeImages(imgCellLabels);
    }
    
    /**
     * save images objects population
     * @param imgNuc
     * @param cellsPop
     * @param geneRefPop
     * @param geneXPop
     * @param outDirResults
     * @param rootName
     */
    public static void saveDotsImage (ImagePlus imgNuc, Objects3DPopulation cellsPop, Objects3DPopulation geneRefPop, Objects3DPopulation geneXPop,
            String outDirResults, String rootName) {
        // red dots geneRef , dots green geneX, blue nucDilpop
        ImageHandler imgCells = ImageHandler.wrap(imgNuc).createSameDimensions();
        ImageHandler imgDotsGeneRef = ImageHandler.wrap(imgNuc).createSameDimensions();
        ImageHandler imgDotsGeneX = ImageHandler.wrap(imgNuc).createSameDimensions();
        ImageHandler imgCellNumbers = ImageHandler.wrap(imgNuc).createSameDimensions();
        // draw nucleus dots population
        cellsPop.draw(imgCells, 255);
        labelsObject(cellsPop, imgCellNumbers.getImagePlus(), 24);
        geneRefPop.draw(imgDotsGeneRef, 255);
        geneXPop.draw(imgDotsGeneX, 255);
        ImagePlus[] imgColors = {imgDotsGeneRef.getImagePlus(), imgDotsGeneX.getImagePlus(), imgCells.getImagePlus(), null, imgCellNumbers.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_DotsObjects.tif");
        imgCells.closeImagePlus();
        imgDotsGeneRef.closeImagePlus();
        imgDotsGeneX.closeImagePlus();
    }
}
