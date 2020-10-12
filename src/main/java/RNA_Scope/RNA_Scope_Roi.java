/*
 * Measure integrated intensities in Rois
 * For Gene reference and gene x
 */
package RNA_Scope;


import static RNA_Scope.RNA_Scope.deconv;
import static RNA_Scope.RNA_Scope.singleDotIntGeneRef;
import static RNA_Scope.RNA_Scope.singleDotIntGeneX;
import static RNA_Scope_Utils.RNA_Scope_Processing.closeImages;
import static RNA_Scope_Utils.RNA_Scope_Processing.findGenePop;
import static RNA_Scope_Utils.RNA_Scope_Processing.find_background;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
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
import loci.common.Region;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.image3d.ImageHandler;


/**
 *
 * @author phm
 */
public class RNA_Scope_Roi implements PlugIn {

private String imageDir = "";
private String outDirResults = "";
private final Calibration cal = new Calibration();   

    /*
    * Integrated intensity
    */
    public static double find_Integrated(ImagePlus img) {

        ResultsTable rt = new ResultsTable();
        Analyzer ana = new Analyzer(img, Measurements.INTEGRATED_DENSITY, rt);
        double intDen = 0; 
        int index = 0;
        for (int z = 1; z <= img.getNSlices(); z++) {
            img.setSlice(z);
            ana.measure();
            intDen += rt.getValue("RawIntDen", index);
            index++;
        }
        System.out.println("Raw Int Den = " + intDen);
        return(intDen);  
    }
    
    /**
     * Dialog ask for channels order
     * @param channels
     * @return ch
     */
    public static ArrayList dialog(String[] channels) {
        ArrayList ch = new ArrayList();
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("DAPI           : ", channels, channels[0]);
        gd.addChoice("Gene Reference : ", channels, channels[1]);
        gd.addChoice("Gene X         : ", channels, channels[2]);
        gd.addMessage("Single dot calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Gene reference single dot mean intensity : ", singleDotIntGeneRef, 0);
        gd.addNumericField("Gene X single dot mean intensity : ", singleDotIntGeneX, 0);
        
        gd.showDialog();
        ch.add(0, gd.getNextChoice());
        ch.add(1, gd.getNextChoice());
        ch.add(2, gd.getNextChoice());
        singleDotIntGeneRef = gd.getNextNumber();
        singleDotIntGeneX = gd.getNextNumber();
        
        if(gd.wasCanceled())
            ch = null;
        return(ch);
    }
    
    /**
     * save images objects population
     * @param img
     * @param geneRefPop
     * @param geneXPop
     * @param outDirResults
     * @param rootName
     */
    public static void saveDotsImage (ImagePlus img, Objects3DPopulation geneRefPop, Objects3DPopulation geneXPop,
            String outDirResults, String rootName) {
        // red dots geneRef , dots green geneX, blue nucDilpop
        ImageHandler imgDotsGeneRef = ImageHandler.wrap(img).createSameDimensions();
        ImageHandler imgDotsGeneX = ImageHandler.wrap(img).createSameDimensions();
        geneRefPop.draw(imgDotsGeneRef, 255);
        geneXPop.draw(imgDotsGeneX, 255);
        ImagePlus[] imgColors = {imgDotsGeneRef.getImagePlus(), imgDotsGeneX.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_DotsObjects.tif");
        imgDotsGeneRef.closeImagePlus();
        imgDotsGeneX.closeImagePlus();
    }
    
  @Override
    public void run(String arg) {
        try {
            imageDir = IJ.getDirectory("Choose directory containing nd files...");
            if (imageDir == null) {
                return;
            }
            File inDir = new File(imageDir);
            String[] imageFile = inDir.list();
            if (imageFile == null) {
                System.out.println("No Image found in "+imageDir);
                return;
            }
            // create output folder
            outDirResults = inDir + File.separator+ "Results"+ File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            FileWriter  fwAnalyze_detail = new FileWriter(outDirResults + "results.xls",false);
            BufferedWriter output_Analyze = new BufferedWriter(fwAnalyze_detail);

            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            Arrays.sort(imageFile);
            int imageNum = 0; 
            String rootName = "";
            ArrayList<String> ch = new ArrayList();
            for (int i = 0; i < imageFile.length; i++) {
                // Find images files
                if (imageFile[i].endsWith(".nd") || imageFile[i].endsWith(".ics")) {
                    if (imageFile[i].endsWith(".nd")) {
                        rootName = imageFile[i].replace(".nd", "");
                        deconv = false;
                    }
                    else {
                        rootName = imageFile[i].replace(".ics", "");
                        deconv = true;
                    }
                    String imageName = inDir+ File.separator+imageFile[i];
                    reader.setId(imageName);
                    int sizeZ = reader.getSizeZ();
                    int sizeC = reader.getSizeC();
                    String[] channels = new String[sizeC];
                    String channelsID = meta.getImageName(0);
                    if (!deconv)
                        channels = channelsID.replace("_", "-").split("/");
                    else 
                        for (int c = 0; c < sizeC; c++) 
                            channels[c] = meta.getChannelExcitationWavelength(0, c).value().toString();
                    imageNum++;
                    // Check calibration
                    if (imageNum == 1) {
                        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
                        cal.pixelHeight = cal.pixelWidth;
                        cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
                        cal.setUnit("microns");
                        System.out.println("x/y cal = " +cal.pixelWidth+", z cal = " + cal.pixelDepth+", stack size = "+sizeZ);
                        
                        // return the index for channels DAPI, Astro, Dots and ask for calibration if needed
                        ch = dialog(channels);
                        if (ch == null) {
                            IJ.showStatus("Plugin cancelled !!!");
                            return;
                        }
                        
                        // write headers
                        output_Analyze.write("Image Name\tCells Integrated intensity in gene ref. channel\tMean background intensity in ref. channel\t"
                            + "Total dots gene ref. (based on cells intensity)\tDots ref. volume (pixel3)\tIntegrated intensity of dots ref. channel\t"
                            + "Total dots gene ref (based on dots seg intensity)\tCells Integrated intensity in gene X channel\tMean background intensity in X channel\t"
                            + "Total dots gene X (based on cells intensity)\tDots X volume (pixel3)\tIntegrated intensity of dots X channel\tTotal dots gene X (based on dots seg intensity)\n");
                        output_Analyze.flush();
                    }
                    
                    // Find roi file name
                    String roiFile = inDir+ File.separator + rootName + ".zip";
                    if (!new File(roiFile).exists()) {
                        IJ.showStatus("No roi file found !") ;
                        return;
                    }
                    else {
                        double geneRefInt = 0, geneRefDotsInt = 0, geneRefBgInt = 0, geneRefIntCor = 0, geneRefDotsIntCor = 0,
                                geneXInt = 0, geneXDotsInt = 0, geneXBgInt = 0, geneXIntCor = 0, geneXDotsIntCor = 0;
                        double geneRefVol = 0, geneXVol = 0, geneRefDotsVol = 0, geneXDotsVol = 0;
                        reader.setSeries(0);
                        ImporterOptions options = new ImporterOptions();
                        options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                        options.setId(imageName);
                        options.setSplitChannels(true);  
                        options.setCBegin(0, 1);
                        options.setCEnd(0, 2);
                        options.setQuiet(true);
                        options.setCrop(true);
                        
                        RoiManager rm = new RoiManager(false);
                        rm.runCommand("Open", roiFile);
                        
                        ImagePlus imgGeneRef = new ImagePlus(), imgGeneX = new ImagePlus();
                        Objects3DPopulation geneRefPop = new Objects3DPopulation();
                        Objects3DPopulation geneXPop = new Objects3DPopulation();
                        
                        // for all rois crop image
                        for (int r = 0; r < rm.getCount(); r++) {
                            Roi roi = rm.getRoi(r);
                            String roiName = roi.getName();
                            Rectangle rect = roi.getBounds();
                            Region reg = new Region(rect.x, rect.y, rect.width, rect.height);
                            options.setCropRegion(0, reg);
                            
                            
                            // gene reference
                            if (roiName.contains("ref")) {
                                // Open Gene reference channel
                                System.out.println("Opening reference gene channel ...");
                                imgGeneRef = BF.openImagePlus(options)[0];
                                if (roiName.contains("bg")) 
                                    geneRefBgInt = find_background(imgGeneRef, 1, imgGeneRef.getNSlices());
                                else {
                                    geneRefInt = find_Integrated(imgGeneRef);
                                    geneRefVol = imgGeneRef.getNSlices()*imgGeneRef.getWidth()*imgGeneRef.getHeight();
                                    geneRefPop = findGenePop(imgGeneRef);
                                }
                                
                                geneRefIntCor = geneRefInt - geneRefBgInt*geneRefVol;
                                
                            }
                            
                            // gene X
                            if (roiName.contains("x")) {
                                // Open Gene reference channel
                                System.out.println("Opening X gene channel ...");
                                imgGeneX = BF.openImagePlus(options)[1];
                                if (roiName.contains("bg")) 
                                    geneXBgInt = find_background(imgGeneX, 1, imgGeneX.getNSlices());
                                else {
                                    geneXInt = find_Integrated(imgGeneX);
                                    geneXVol = imgGeneX.getNSlices()*imgGeneX.getWidth()*imgGeneX.getHeight();
                                    geneXPop = findGenePop(imgGeneX);
                                }
                                
                                geneXIntCor = geneXInt - geneXBgInt*geneXVol;
                            }
                        }
                        // save dots image
                        saveDotsImage(imgGeneX, geneRefPop, geneXPop, outDirResults, rootName);
                        
                        // find dots integrated intensity
                        for (int n = 0; n < geneRefPop.getNbObjects(); n++) {
                            Object3D dotobj = geneRefPop.getObject(n);
                            geneRefDotsInt += dotobj.getIntegratedDensity(ImageHandler.wrap(imgGeneRef));
                            geneRefDotsVol += dotobj.getVolumePixels();
                        }
                        geneRefDotsIntCor = geneRefDotsInt - geneRefBgInt*geneRefDotsVol;
                        
                        for (int n = 0; n < geneXPop.getNbObjects(); n++) {
                            Object3D dotobj = geneXPop.getObject(n);
                            geneXDotsInt += dotobj.getIntegratedDensity(ImageHandler.wrap(imgGeneX));
                            geneXDotsVol += dotobj.getVolumePixels();
                        }
                        geneXDotsIntCor = geneXDotsInt - geneXBgInt*geneXDotsVol;
                        
                        closeImages(imgGeneRef);
                        closeImages(imgGeneX);
                        
                        output_Analyze.write(rootName+"\t"+geneRefInt+"\t"+geneRefBgInt+"\t"+(geneRefIntCor/singleDotIntGeneRef)+"\t"+geneRefDotsVol+"\t"+geneRefDotsInt+"\t"+
                                (geneRefDotsIntCor/singleDotIntGeneRef)+"\t"+geneXInt+"\t"+geneXBgInt+"\t"+(geneXIntCor/singleDotIntGeneX)+"\t"+geneXDotsVol+"\t"+geneXDotsInt+"\t"+
                                (geneXDotsIntCor/singleDotIntGeneX)+"\n");
                        output_Analyze.flush();
                    }
                }
            }
            output_Analyze.close();
            IJ.showStatus("Process done ...");
        } catch (DependencyException | ServiceException | FormatException | IOException ex) {
            Logger.getLogger(RNA_Scope_Roi.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

                    
                              
}
