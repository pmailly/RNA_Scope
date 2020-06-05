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
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
import ij.plugin.GaussianBlur3D;
import ij.plugin.ZProjector;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
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
import org.apache.tools.ant.taskdefs.WaitFor;
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
        gd.addCheckbox("Adult", false);
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
     * @param img
     * @return pop objects population
     */

    public static  Objects3DPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        return pop;
    }
    
    
    /*Median filter 
     * 
     * @param img
     * @param size
     */ 
    public static void median_filter(ImagePlus img, double size) {
        RankFilters median = new RankFilters();
        for (int s = 1; s <= img.getNSlices(); s++) {
            img.setZ(s);
            median.rank(img.getProcessor(), size, RankFilters.MEDIAN);
            img.updateAndDraw();
        }
    }

     /**
     * Find gene population
     * @param imgGeneRef
     * @return genePop
     */
    public static Objects3DPopulation findGenePop(ImagePlus imgGeneRef) {
        ImagePlus img = imgGeneRef.duplicate();
        median_filter(img, 1);
        IJ.run(img, "Difference of Gaussians", "  sigma1=2 sigma2=1 stack");
        Prefs.blackBackground = false;
        IJ.run(img, "Convert to Mask","method=IsoData background=Dark"); 
        Objects3DPopulation genePop = getPopFromImage(img);
        closeImages(img);
        System.out.println(genePop.getNbObjects()+" genes found");
        return(genePop);
    }
    
    /**
     * ramdom color nucleus population
     */
    public static ImagePlus colorPop (Objects3DPopulation cellsPop,  ImagePlus img) {
        //create image objects population
        ImageHandler imgObj = ImageInt.wrap(img).createSameDimensions();
        imgObj.set332RGBLut();
        imgObj.setCalibration(img.getCalibration());
        for (int i = 0; i < cellsPop.getNbObjects(); i++) {
            Object3D obj = cellsPop.getObject(i);
            obj.draw(imgObj, (i+1));
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
    
    public static ArrayList<Cell> tagsCells(Objects3DPopulation cellsPop, Objects3DPopulation dotsPop, ImagePlus imgGeneRef, ImagePlus imgGeneX) {
        IJ.showStatus("Finding cells with gene reference ...");
        ArrayList<Cell> cells = new ArrayList<>();
        ImageHandler imhRef = ImageHandler.wrap(imgGeneRef);
        ImageHandler imhX = ImageHandler.wrap(imgGeneX);
        int index = 0;
        
        for (int i = 0; i < cellsPop.getNbObjects(); i++) {
            int geneRefDots = 0;
            double cellVol, geneRefMeanDotsVol;
            double geneRefMeanInt, geneRefInt;
            double geneRefDotsInt = 0;
            double geneRefDotMaxInt = 0;
            double geneXInt;
            // calculate cell parameters
            index++;
            Object3D cellObj = cellsPop.getObject(i);
            cellVol = cellObj.getVolumeUnit();
            geneRefMeanInt = cellObj.getPixMeanValue(imhRef);
            geneRefInt = cellObj.getIntegratedDensity(imhRef);
            geneXInt = cellObj.getIntegratedDensity(imhX);
            double dotVol = 0;
            for (int n = 0; n < dotsPop.getNbObjects(); n++) {
                // dots parameters
                Object3D dotObj = dotsPop.getObject(n);
                // find dots inside cell
                if (dotObj.hasOneVoxelColoc(cellObj)) {
                    geneRefDots++;
                    dotVol += dotObj.getVolumeUnit();
                    double dotInt = dotObj.getIntegratedDensity(imhRef);
                    // find dot max intensity
                    if (dotInt > geneRefDotMaxInt)
                        geneRefDotMaxInt = dotInt;
                    geneRefDotsInt += dotInt;
                }
            }
            geneRefMeanDotsVol = dotVol / geneRefDots;
            Cell cell = new Cell(index, false, cellVol, geneRefMeanInt, geneRefInt, geneRefDots, geneRefMeanDotsVol,
                    geneRefDotsInt, geneRefDotMaxInt, geneXInt);
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
        for (int o = 0; o < nucPop.getNbObjects(); o++) {
            Object3D obj = nucPop.getObject(o);
            cellsPop.addObject(obj.getDilatedObject((float)(nucDil/cal.pixelWidth), (float)(nucDil/cal.pixelHeight), (float)(nucDil/cal.pixelDepth)));
        }
        return(cellsPop);
    }
    
    /**
     * Nucleus segmentation 2
     * @param imgNuc
     * @return 
     */
    public static Objects3DPopulation find_nucleus2(ImagePlus imgNuc) {
        ImagePlus img = imgNuc.duplicate();
        //IJ.run(img, "Subtract Background...", "rolling=150 stack");
        ImageStack stack = new ImageStack(imgNuc.getWidth(), imgNuc.getHeight());
        for (int i = 1; i <= img.getStackSize(); i++) {
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
        ImagePlus imgWater = WatershedSplit(imgStack, 8);
        closeImages(imgStack);
        imgWater.setCalibration(imgNuc.getCalibration());
        Objects3DPopulation cellPop = new Objects3DPopulation(imgWater);
        cellPop.removeObjectsTouchingBorders(imgWater, false);
        closeImages(imgWater);
        closeImages(img);
        return(cellPop);
    }
    
    
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
                + "Integrated intensity in gene X channel\tNb gene ref. dots\tMean gene ref. dots volume\tSum of dots ref. integrated intensity\t"
                + "Max of dots ref. integrated intensity\tNegative cell mean intensity in gene ref. channel\tNegative cell integrated intensity in gene ref. channel"
                + "\tEstimated mean intensity background in gene ref. channel\tEstimated mean intensity background in gene X channel\n");
        output_detail_Analyze.flush();
    }
    
    
    
}
