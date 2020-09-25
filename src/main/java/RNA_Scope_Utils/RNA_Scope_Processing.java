package RNA_Scope_Utils;


import static RNA_Scope.RNA_Scope.adult;
import static RNA_Scope.RNA_Scope.cal;
import static RNA_Scope.RNA_Scope.maxNucVol;
import static RNA_Scope.RNA_Scope.minNucVol;
import static RNA_Scope.RNA_Scope.nucDil;
import static RNA_Scope.RNA_Scope.output_detail_Analyze;
import static RNA_Scope.RNA_Scope.removeSlice;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.GaussianBlur3D;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
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
    
    private static double pixDepth  = 1;
    public static CLIJ2 clij2 = CLIJ2.getInstance();

    
    /**
     * Dialog ask for channels order
     * @param channels
     * @param showCal
     * @param cal
     * @return ch    private boolean showCal = false;

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
        gd.setInsets(0, 120, 0);
        gd.addCheckbox("Nucleus detection adult", false);
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
        adult = gd.getNextBoolean();
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
   * Erode
   * USING CLIJ2
   * @param imgCL
   * @return imgCLOut
   */
    private static ClearCLBuffer erode(ClearCLBuffer imgCL) {
        ClearCLBuffer imgCLOut = clij2.create(imgCL);
        clij2.erodeBox(imgCL, imgCLOut);
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
        ClearCLBuffer imgCLDOG = DOG(imgCLMed, 1, 1, 1, 2, 2, 2);
        clij2.release(imgCLMed);
        ClearCLBuffer imgCLBin = threshold(imgCLDOG, "IsoData", false); 
        clij2.release(imgCLDOG);
        Objects3DPopulation genePop = getPopFromClearBuffer(imgCLBin);
        clij2.release(imgCLBin);
        return(genePop);
    }
    
    
    /**
     * ramdom color nucleus population
     */
    public static ImagePlus colorPop (Objects3DPopulation cellsPop,  ImagePlus img) {
        //create image objects population
        ImageHandler imgObj = ImageInt.wrap(img).createSameDimensions();
        imgObj.setCalibration(img.getCalibration());
        for (int i = 0; i < cellsPop.getNbObjects(); i++) {
            int color = (int)(Math.random() * (255 - 1 + 1) + 1);
            Object3D obj = cellsPop.getObject(i);
            obj.draw(imgObj, (color));
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
    
    public static ArrayList<Cell> tagsCells(Objects3DPopulation cellsPop, Objects3DPopulation dotsRefPop, Objects3DPopulation dotsXPop, ImagePlus imgGeneRef, ImagePlus imgGeneX) {
        IJ.showStatus("Finding cells with gene reference ...");
        ArrayList<Cell> cells = new ArrayList<>();
        ImageHandler imhRef = ImageHandler.wrap(imgGeneRef);
        ImageHandler imhX = ImageHandler.wrap(imgGeneX);
        int index = 0;
        
        for (int i = 0; i < cellsPop.getNbObjects(); i++) {
            int geneRefDots = 0, geneXDots = 0;
            double cellVol, geneRefMeanDotsVol, geneXMeanDotsVol;
            double geneRefMeanInt, geneRefInt, geneXMeanInt, geneXInt;
            double geneRefDotsInt = 0, geneXDotsInt = 0;
            double geneRefDotMaxInt = 0, geneXDotMaxInt = 0;
            
            // calculate cell parameters
            index++;
            Object3D cellObj = cellsPop.getObject(i);
            cellVol = cellObj.getVolumeUnit();
            geneRefMeanInt = cellObj.getPixMeanValue(imhRef);
            geneXMeanInt = cellObj.getPixMeanValue(imhX);
            geneRefInt = cellObj.getIntegratedDensity(imhRef);
            geneXInt = cellObj.getIntegratedDensity(imhX);
            double dotRefVol = 0, dotXVol = 0;
            for (int n = 0; n < dotsRefPop.getNbObjects(); n++) {
                // dots parameters
                Object3D dotObj = dotsRefPop.getObject(n);
                // find dots inside cell
                if (dotObj.hasOneVoxelColoc(cellObj)) {
                    geneRefDots++;
                    dotRefVol += dotObj.getVolumeUnit();
                    double dotInt = dotObj.getIntegratedDensity(imhRef);
                    // find dot max intensity
                    if (dotInt > geneRefDotMaxInt)
                        geneRefDotMaxInt = dotInt;
                    geneRefDotsInt += dotInt;
                }
            }
            for (int n = 0; n < dotsXPop.getNbObjects(); n++) {
                // dots parameters
                Object3D dotObj = dotsXPop.getObject(n);
                // find dots inside cell
                if (dotObj.hasOneVoxelColoc(cellObj)) {
                    geneXDots++;
                    dotXVol += dotObj.getVolumeUnit();
                    double dotInt = dotObj.getIntegratedDensity(imhX);
                    // find dot max intensity
                    if (dotInt > geneXDotMaxInt)
                        geneXDotMaxInt = dotInt;
                    geneXDotsInt += dotInt;
                }
            }
            geneRefMeanDotsVol = dotRefVol / geneRefDots;
            geneXMeanDotsVol = dotXVol / geneXDots;
            Cell cell = new Cell(index, false, cellVol, geneRefMeanInt, geneRefInt, geneRefDots, geneRefMeanDotsVol,
                    geneRefDotsInt, geneRefDotMaxInt, geneXMeanInt, geneXInt, geneXDots, geneXMeanDotsVol,
                    geneXDotsInt, geneXDotMaxInt);
            cells.add(cell);
        }
        return(cells);
    }
    
    public static  Objects3DPopulation findNucleus(ImagePlus imgNuc) {
        Objects3DPopulation nucPopOrg = new Objects3DPopulation();
        if (adult)
            nucPopOrg = find_nucleus2(imgNuc);
        else
            nucPopOrg = find_nucleus3(imgNuc);

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
                cellsPop.addObject(obj.getDilatedObject((float)(nucDil/cal.pixelWidth), (float)(nucDil/cal.pixelHeight), (float)(nucDil/cal.pixelDepth)));
            }
        else
            cellsPop.addObjects(nucPop.getObjectsList());
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
        IJ.run(img, "Remove Outliers", "block_radius_x=50 block_radius_y=50 standard_deviations=1 stack");
        IJ.run(img, "Difference of Gaussians", "  sigma1=30 sigma2=10 stack");
        ImageStack stack = new ImageStack(img.getWidth(), imgNuc.getHeight());
        for (int i = 1; i <= img.getStackSize(); i++) {
            img.setZ(i);
            img.updateAndDraw();
            IJ.run(img, "Nuclei Outline", "blur=0 blur2=0 threshold_method=Triangle outlier_radius=0 outlier_threshold=1 max_nucleus_size=500 "
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
        ImagePlus imgWater = WatershedSplit(imgStack, 8);
        closeImages(imgStack);
        imgWater.setCalibration(imgNuc.getCalibration());
        Objects3DPopulation cellPop = new Objects3DPopulation(imgWater);
        cellPop.removeObjectsTouchingBorders(imgWater, false);
        closeImages(imgWater);
        closeImages(img);
        return(cellPop);
    }
    
//      /**
//     * Nucleus segmentation 2 old version
//     * @param imgNuc
//     * @return 
//     * @return cellPop
//     */
//    public static Objects3DPopulation find_nucleus2(ImagePlus imgNuc) {
//        ImagePlus img = new Duplicator().run(imgNuc);
//        removeOutliers(img, 15, 15, 3);
//        ClearCLBuffer imgCL = clij2.push(imgNuc);
//        ClearCLBuffer imgCLDOG = DOG(imgCL, 20, 20, 20, 30, 30, 30);
//        clij2.release(imgCL);
//        ImagePlus imgDOG = clij2.pull(imgCLDOG);
//        clij2.release(imgCLDOG);
//        ImageStack stack = new ImageStack(imgDOG.getWidth(), imgNuc.getHeight());
//        for (int i = 1; i <= imgDOG.getStackSize(); i++) {
//            imgDOG.setZ(i);
//            imgDOG.updateAndDraw();
//            IJ.run(imgDOG, "Nuclei Outline", "blur=0 blur2=0 threshold_method=Triangle outlier_radius=0 outlier_threshold=1 max_nucleus_size=500 "
//                    + "min_nucleus_size=10 erosion=5 expansion_inner=5 expansion=5 results_overlay");
//            imgDOG.setZ(1);
//            imgDOG.updateAndDraw();
//            ImagePlus mask = new ImagePlus("mask", imgDOG.createRoiMask().getBufferedImage());
//            ImageProcessor ip =  mask.getProcessor();
//            ip.invertLut();
//            for (int n = 0; n < 3; n++) 
//                ip.erode();
//            stack.addSlice(ip);
//        }
//        ImagePlus imgStack = new ImagePlus("Nucleus", stack);
//        ImagePlus imgWater = WatershedSplit(imgStack, 8);
//        closeImages(imgStack);
//        imgWater.setCalibration(imgNuc.getCalibration());
//        Objects3DPopulation cellPop = new Objects3DPopulation(imgWater);
//        cellPop.removeObjectsTouchingBorders(imgWater, false);
//        closeImages(imgWater);
//        closeImages(imgDOG);
//        return(cellPop);
//    }
            
            
    /**
     * Nucleus segmentation 3
     * @param imgNuc
     * @return 
     */
    public static Objects3DPopulation find_nucleus3(ImagePlus imgNuc) {
        ImagePlus img = imgNuc.duplicate();
        img.setTitle("Nucleus");
        IJ.run(img, "Subtract Background...", "rolling=150 stack");
        IJ.run(img, "Remove Outliers...", "radius=15 threshold=1 which=Bright stack");
        IJ.run(img, "Difference of Gaussians", "  sigma1=20 sigma2=15 enhance stack");
        img.setZ(img.getNSlices()/2);
        img.updateAndDraw();
        IJ.run(img, "Find Maxima...", "prominence=5 output=[Point Selection]");
        IJ.run(img, "Cell Outliner", "cell_radius=50 tolerance=0.80 kernel_width=15 dark_edge kernel_smoothing=2 polygon_smoothing=2 weighting_gamma=1 iterations=1 dilate=1 all_slices");IJ.run(img, "Remove Outliers...", "radius=15 threshold=1 which=Bright stack");
        ImagePlus imgCells = WindowManager.getImage(img.getTitle()+" Cell Outline");
        imgCells.hide();
        imgCells.setCalibration(imgNuc.getCalibration());
        IJ.run(imgCells, "Select None", "");
        Objects3DPopulation cellPop = new Objects3DPopulation(imgCells);
        cellPop.removeObjectsTouchingBorders(imgCells, false);
        closeImages(imgCells);
        closeImages(img);
        return(cellPop);
    }
    
      /**
     * Find negative cells
     * 
     * @param rm
     * @param imgGeneRef
     * @param cellsPop
     * @param listCells
     * @return 
     */
    public static double[] find_negativeCell(RoiManager rm, ImagePlus imgGeneRef, Objects3DPopulation cellsPop, ArrayList<Cell> listCells) {
        double cellInt = 0;
        double cellMeanInt = 0;
        double[] cellParams = new double[2];
        ImageHandler imh = ImageHandler.wrap(imgGeneRef);
        for (int r = 0; r < rm.getCount(); r++) {
            rm.select(imgGeneRef,r);
            imgGeneRef.updateAndDraw();
            Roi cell = imgGeneRef.getRoi();
            Point3D pt = new Point3D(cell.getXBase(), cell.getYBase(), cell.getZPosition());
            for (int n = 0; n < cellsPop.getNbObjects(); n++) {
                Object3D obj = cellsPop.getObject(n);
                if (obj.inside(pt)) {
                   listCells.get(n).setNegative(true);
                   obj.setName("n");
                   cellInt +=  obj.getIntegratedDensity(imh);
                   cellMeanInt += obj.getPixMeanValue(imh);
                   break;
                }
            }
        }
        cellParams[0] = cellMeanInt/rm.getCount();
        cellParams[1] = cellInt/rm.getCount();
        return cellParams;
    }  
    
      /**
     * Find negative cells from xml file
     * 
     * @param pts
     * @param imgGeneRef
     * @param cellsPop
     * @param listCells
     * @return 
     */
    public static double[] find_negativeCell(ArrayList<Point3D> pts, ImagePlus imgGeneRef, Objects3DPopulation cellsPop, ArrayList<Cell> listCells) {
        double cellInt = 0;
        double cellMeanInt = 0;
        double[] cellParams = new double[2];
        ImageHandler imh = ImageHandler.wrap(imgGeneRef);
        for (int p = 0; p < pts.size(); p++) {
            Point3D pt = new Point3D(pts.get(p).x, pts.get(p).y,pts.get(p).z);
            for (int n = 0; n < cellsPop.getNbObjects(); n++) {
                Object3D obj = cellsPop.getObject(n);
                if (obj.inside(pt)) {
                   listCells.get(n).setNegative(true);
                   obj.setName("n");
                   cellInt +=  obj.getIntegratedDensity(imh);
                   cellMeanInt += obj.getPixMeanValue(imh);
                   break;
                }
            }
        }
        cellParams[0] = cellMeanInt/pts.size();
        cellParams[1] = cellInt/pts.size();
        return cellParams;
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
    
    
    /*
    * Mean background intensity
    * Z project min intensity
    * gaussian blur 100 and calculate Integrated intensity
    */
    public static double find_background(ImagePlus img) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(ZProjector.MIN_METHOD);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
        ImagePlus imgProj = zproject.getProjection();
        ImageProcessor imp = imgProj.getProcessor();
        GaussianBlur gaussian = new GaussianBlur();
        gaussian.blurGaussian(imp, 100, 100, 0.02);
        double bgInt = imp.getStats().mean;
        System.out.println("Mean Background = " + bgInt);
        closeImages(imgProj);
        return(bgInt);  
    }
    
    /**
     * Label object
     * @param popObj
     * @param img 
     */
    public static void labelsObject (Objects3DPopulation popObj, ImagePlus img) {
        Font tagFont = new Font("SansSerif", Font.PLAIN, 24);
        String name;
        for (int n = 0; n < popObj.getNbObjects(); n++) {
            Object3D obj = popObj.getObject(n);
            if ("".equals(obj.getName())) 
                name = Integer.toString(n+1);
            else
                name = Integer.toString(n+1)+"*";
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
     * Draw negative cells (name = n)
     * @param cellsPop
     * @param imh
     */
    public static void drawNegCells(Objects3DPopulation cellsPop, ImageHandler imh) {
        for (int n = 0; n < cellsPop.getNbObjects(); n++) {
            Object3D obj = cellsPop.getObject(n);
            if ("n".equals(obj.getName()))
               obj.draw(imh, n);
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
        output_detail_Analyze.write("Image Name\t#Cell\tCell Vol\tCell negative\tIntegrated intensity in gene ref. channel\tMean intensity in gene ref. channel\t"
                + "Nb gene ref. dots\tMean gene ref. dots volume\tIntegrated intensity of dots ref.channel\tMax of dots ref. integrated intensity\t"
                + "Integrated intensity in gene X channel\tMean intensity in gene X channel\tNb gene X dots\tMean gene X dots volume\tIntegrated intensity of dots X\t"
                + "Max of dots X integrated intensity\tNegative cell mean intensity in gene ref. channel\tNegative cell integrated intensity in gene ref. channel"
                + "\tEstimated mean intensity background in gene ref. channel\tEstimated mean intensity background in gene X channel\n");
        output_detail_Analyze.flush();
    }
    
    /**
     * Save nucleus with random colors
     * @param imgNuc
     * @param cellsPop
     * @param outDirResults
     * @param rootName
     */
    public static void saveNucleus (ImagePlus imgNuc, Objects3DPopulation cellsPop, String outDirResults, String rootName) {
        ImagePlus imgColorPop = colorPop (cellsPop, imgNuc);
        IJ.run(imgColorPop, "3-3-2 RGB", "");
        FileSaver ImgColorObjectsFile = new FileSaver(imgColorPop);
        ImgColorObjectsFile.saveAsTiff(outDirResults + rootName + "_Nucleus-ColorObjects.tif");
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
    public static void saveNucleusLabelledImage (ImagePlus imgNuc, Objects3DPopulation cellsPop, ImagePlus imgGeneRef, ImagePlus imgGeneX,
            String outDirResults, String rootName) {
        // red geneRef , green geneX, blue nucDilpop
        ImageHandler imgCells = ImageHandler.wrap(imgNuc).createSameDimensions();
        ImageHandler imgNegCells = ImageHandler.wrap(imgNuc).createSameDimensions();
        ImagePlus imgCellLabels = ImageHandler.wrap(imgNuc).createSameDimensions().getImagePlus();
        // draw nucleus population
        cellsPop.draw(imgCells, 255);
        drawNegCells(cellsPop, imgNegCells);
        labelsObject(cellsPop, imgCellLabels);
        ImagePlus[] imgColors = {imgGeneRef, imgGeneX, imgCells.getImagePlus(),null,imgNegCells.getImagePlus(),null,imgCellLabels};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_Objects.tif");
        imgCells.closeImagePlus();
        imgNegCells.closeImagePlus();
        closeImages(imgCellLabels);
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
    public static void saveDotsImage (ImagePlus imgNuc, Objects3DPopulation cellsPop, Objects3DPopulation geneRefPop, Objects3DPopulation geneXPop,
            String outDirResults, String rootName) {
        // red dots geneRef , dots green geneX, blue nucDilpop
        ImageHandler imgCells = ImageHandler.wrap(imgNuc).createSameDimensions();
        ImageHandler imgDotsGeneRef = ImageHandler.wrap(imgNuc).createSameDimensions();
        ImageHandler imgDotsGeneX = ImageHandler.wrap(imgNuc).createSameDimensions();
        // draw nucleus dots population
        cellsPop.draw(imgCells, 255);
        geneRefPop.draw(imgDotsGeneRef, 255);
        geneXPop.draw(imgDotsGeneX, 255);
        ImagePlus[] imgColors = {imgDotsGeneRef.getImagePlus(), imgDotsGeneX.getImagePlus(), imgCells.getImagePlus()};
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
