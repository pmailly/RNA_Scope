/*
 * Calibration steps for RNAScope
 * Analyze background on rois and dots volume, intensity using xml file 
 * in images containing single dots
 */
package RNA_Scope;


import RNA_Scope_Utils.Dot;
import RNA_Scope_Utils.RNA_Scope_Processing;
import static RNA_Scope_Utils.RNA_Scope_Processing.closeImages;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
import mcib3d.image3d.ImageHandler;
import org.xml.sax.SAXException;


/**
 *
 * @author phm
 */
public class RNA_Scope_Calib implements PlugIn {

private String imageDir = "";
private String outDirResults = "";
private final Calibration cal = new Calibration();   
private static BufferedWriter output_dotCalib;

    /*
    * Get Mean of intensity in stack
    */
    private double bgIntensity(ImagePlus img, Roi roi, int zMin, int zMax) {
        img.setRoi(roi);
        ImagePlus imgCrop = new Duplicator().run(img);
        ResultsTable rt = new ResultsTable();
        Analyzer ana = new Analyzer(imgCrop, Measurements.INTEGRATED_DENSITY, rt);
        double intDen = 0;
        int index = 0;
        for (int z = zMin; z <= zMax; z++) {
            imgCrop.setSlice(z);
            ana.measure();
            intDen += rt.getValue("RawIntDen", index);
            index++;
        }
        double vol = imgCrop.getWidth()+imgCrop.getHeight()*imgCrop.getNSlices();
        double bgInt = intDen / vol;
        System.out.println("Mean Background for "+roi.getName() + " = " + bgInt);
        closeImages(imgCrop);
        return(bgInt);  
    }
    
    
    /**
     * Find pointed single dots in dotsPop population
     * @param arg 
     */
    private ArrayList<Dot> findSingleDots(ArrayList<Point3D> pts, Objects3DPopulation dotsPop, Objects3DPopulation pointedDotsPop, ImagePlus img) {
        ImageHandler imh = ImageHandler.wrap(img);
        ArrayList<Dot> dots = new ArrayList();
        int index = 0;
        for (Point3D pt : pts) {
            for (int i = 0; i < dotsPop.getNbObjects(); i++) {
                Object3D dotObj = dotsPop.getObject(i);
                if(dotObj.inside(pt.getRoundX(), pt.getRoundY(), pt.getRoundZ())) {
                    Dot dot = new Dot(index, dotObj.getVolumePixels(), dotObj.getIntegratedDensity(imh), dotObj.getZmin(), dotObj.getZmax(),dotObj.getCenterZ());
                    dots.add(dot);
                    pointedDotsPop.addObject(dotObj);
                    dotsPop.removeObject(dotObj);
                }
            }
            index++;
        }
        return(dots);
    }
    
    
    /**
     * Label object
     * @param popObj
     * @param img 
     */
    private static void labelsObject (Objects3DPopulation popObj, ImagePlus img, int fontSize) {
        Font tagFont = new Font("SansSerif", Font.PLAIN, fontSize);
        String name;
        for (int n = 0; n < popObj.getNbObjects(); n++) {
            Object3D obj = popObj.getObject(n);
            int[] box = obj.getBoundingBox();
            int z = (int)obj.getCenterZ();
            int x = box[0] - 2;
            int y = box[2] - 2;
            img.setSlice(z+1);
            ImageProcessor ip = img.getProcessor();
            ip.setFont(tagFont);
            ip.setColor(255);
            ip.drawString(String.valueOf(n), x, y);
            img.updateAndDraw();
        }
    }
    
     /**
     * save images objects population
     * @param imgNuc
     * @param dotsPop
     * @param outDirResults
     * @param rootName
     */
    public static void saveDotsImage (ImagePlus img, Objects3DPopulation dotsPop, String outDirResults, String rootName) {
        // red dots geneRef , dots green geneX, blue nucDilpop
        ImageHandler imgDots = ImageHandler.wrap(img).createSameDimensions();
        // draw dots population
        dotsPop.draw(imgDots, 255);
        labelsObject(dotsPop, imgDots.getImagePlus(), 12);
        ImagePlus[] imgColors = {null, imgDots.getImagePlus(), null, img.duplicate()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_DotsObjects.tif");
        imgDots.closeImagePlus();
    }
    
    
    
    
  @Override
    public void run(String arg) {
        try {
            imageDir = IJ.getDirectory("Choose directory containing tif, roi and xml files...");
            if (imageDir == null) {
                return;
            }
            File inDir = new File(imageDir);
            String[] imageFile = inDir.list();
            if (imageFile == null) {
                System.out.println("No Image found in "+imageDir);
                return;
            }
            
            Arrays.sort(imageFile);
            int imageNum = 0; 
            String imageExt = "tif";
            String rootName = "";
            ArrayList<String> ch = new ArrayList<>();
            for (int i = 0; i < imageFile.length; i++) {
                // Find tif files
                if (imageFile[i].endsWith(imageExt)) {
                    String imageName = inDir+ File.separator+imageFile[i];
                    rootName = imageFile[i].replace("."+imageExt, "");
                    imageNum++;
                    boolean showCal = false;
                    
                    // Check calibration
                    if (imageNum == 1) {
                        // create output folder
                        outDirResults = inDir + File.separator+ "Results"+ File.separator;
                        File outDir = new File(outDirResults);
                        if (!Files.exists(Paths.get(outDirResults))) {
                            outDir.mkdir();
                        } 
                        // write result file headers
                        FileWriter  fwAnalyze_detail = new FileWriter(outDirResults + "dotsCalibration_results.xls",false);
                        output_dotCalib = new BufferedWriter(fwAnalyze_detail);
                        // write results headers
                        output_dotCalib.write("Image Name\t#Dot\tDot Vol (pixel3)\tDot Integrated Intensity\tMean Dot Background intensity\t"
                                + "Corrected Dots Integrated Intensity\tDot Z center\tDot Z range\tMean intensity per single dot\n");
                        output_dotCalib.flush();
                    }
                    
                    // Find roi file name
                    String roiFile = inDir+ File.separator + rootName + ".zip";
                    if (!new File(roiFile).exists()) {
                        IJ.showStatus("No roi file found !") ;
                        return;
                    }
                    // Find roi file name
                    String xmlFile = inDir+ File.separator + rootName + ".xml";
                    if (!new File(xmlFile).exists()) {
                        IJ.showStatus("No xml file found !") ;
                        return;
                    }
                    else {                        
                        // Open Gene reference channel
                        System.out.println("Opening gene channel ...");
                        ImagePlus img = IJ.openImage(imageName);
                        RoiManager rm = new RoiManager(false);
                        rm.runCommand("Open", roiFile);
                        
                        // Read dots coordinates in xml file
                        ArrayList<Point3D> dotsCenter = RNA_Scope_Processing.readXML(xmlFile);
                        System.out.println("Pointed dots found = "+dotsCenter.size());
                        
                        // 3D dots segmentation
                        Objects3DPopulation dotsPop = RNA_Scope_Processing.findGenePop(img);
                        System.out.println("Total dots found = "+dotsPop.getNbObjects());
                        
                        
                        
                        // find pointed dots in dotsPop
                        Objects3DPopulation pointedDotsPop = new Objects3DPopulation();

                        ArrayList<Dot> dots = findSingleDots(dotsCenter, dotsPop, pointedDotsPop, img);
                        System.out.println("Associated dots = "+dots.size());
                        
                        // Save dots
                        saveDotsImage (img, pointedDotsPop, outDirResults, rootName);
                        
                        // for all rois
                        // find background associated to dot
                        double sumCorIntDots = 0;
                        for (int r = 0; r < rm.getCount(); r++) {
                            Roi roi = rm.getRoi(r);
                            Dot dot = dots.get(r);
                            double bgDotInt = bgIntensity(img, roi, dot.getZmin(), dot.getZmax());
                            double corIntDot = dot.getIntDot() - (bgDotInt * dot.getVolDot());
                            sumCorIntDots += corIntDot;
                            // write results
                            output_dotCalib.write(rootName+"\t"+r+"\t"+dot.getVolDot()+"\t"+dot.getIntDot()+"\t"+bgDotInt+"\t"+corIntDot+
                                    "\t"+dot.getZCenter()+"\t"+(dot.getZmax()-dot.getZmin())+"\n");
                            output_dotCalib.flush();
                        }
                        double MeanIntDot = sumCorIntDots / rm.getCount();
                        output_dotCalib.write("\t\t\t\t\t\t\t\t"+MeanIntDot+"\n");
                    }
                }
            }
            if(new File(outDirResults + "dotsCalibration_results.xls").exists())
                output_dotCalib.close();
            IJ.showStatus("Calibration done");
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            Logger.getLogger(RNA_Scope_Calib.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

                    
                              
}
