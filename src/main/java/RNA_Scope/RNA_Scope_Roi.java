/*
 * Measure integrated intensities in Rois
 * For Gene reference and gene x
 */
package RNA_Scope;


import static RNA_Scope_Utils.RNA_Scope_Processing.closeImages;
import static RNA_Scope_Utils.RNA_Scope_Processing.find_background;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
            String imageExt = "nd";
            String rootName = "";
            for (int i = 0; i < imageFile.length; i++) {
                // Find nd files
                if (imageFile[i].endsWith(imageExt)) {
                    String imageName = inDir+ File.separator+imageFile[i];
                    rootName = imageFile[i].replace("."+ imageExt, "");
                    reader.setId(imageName);
                    int sizeZ = reader.getSizeZ();
                    imageNum++;
                    String channelsID = meta.getImageName(0);
                    if (!channelsID.contains("CSU"))
                       channelsID =  "CSU_405/CSU_488/CSU_561/CSU_642";
                    // Check calibration
                    if (imageNum == 1) {
                        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
                        cal.pixelHeight = cal.pixelWidth;
                        cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
                        cal.setUnit("microns");
                        System.out.println("x/y cal = " +cal.pixelWidth+", z cal = " + cal.pixelDepth+", stack size = "+sizeZ);

                        
                        // write headers
                        output_Analyze.write("Image Name\tGene ref. integrated intensity\tGene ref. mean background intensity\tGene ref. corrected Intensity\t"
                                + "Gene X integrated intensity\tGene X mean background intensity\tGene X corrected intensity\n");
                        output_Analyze.flush();
                    }
                    
                    // Find roi file name
                    String roiFile = inDir+ File.separator + rootName + ".zip";
                    if (!new File(roiFile).exists()) {
                        IJ.showStatus("No roi file found !") ;
                        return;
                    }
                    else {
                        double geneRefInt = 0, geneRefBgInt = 0, geneRefIntCor = 0, 
                                geneXInt = 0, geneXBgInt = 0, geneXIntCor = 0;
                        double geneRefVol = 0, geneXVol = 0;
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
                                ImagePlus imgGeneRef = BF.openImagePlus(options)[0];
                                if (roiName.contains("bg")) 
                                    geneRefBgInt = find_background(imgGeneRef, null);
                                else {
                                    geneRefInt = find_Integrated(imgGeneRef);
                                    geneRefVol = imgGeneRef.getNSlices()*imgGeneRef.getWidth()*imgGeneRef.getHeight();
                                }
                                closeImages(imgGeneRef);
                                geneRefIntCor = geneRefInt - geneRefBgInt*geneRefVol;
                                
                            }
                            
                            // gene X
                            if (roiName.contains("x")) {
                                // Open Gene reference channel
                                System.out.println("Opening X gene channel ...");
                                ImagePlus imgGeneX = BF.openImagePlus(options)[1];
                                if (roiName.contains("bg")) 
                                    geneXBgInt = find_background(imgGeneX, null);
                                else {
                                    geneXInt = find_Integrated(imgGeneX);
                                    geneXVol = imgGeneX.getNSlices()*imgGeneX.getWidth()*imgGeneX.getHeight();
                                }
                                closeImages(imgGeneX);
                                geneXIntCor = geneXInt - geneXBgInt*geneXVol;
                            }
                        }
                        output_Analyze.write(rootName+"\t"+geneRefInt+"\t"+geneRefBgInt+"\t"+geneRefIntCor+"\t"+geneXInt+"\t"+geneXBgInt+"\t"+geneXIntCor+"\n");
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
