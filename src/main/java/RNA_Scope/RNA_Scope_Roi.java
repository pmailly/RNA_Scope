/*
 * Measure integrated intensities in Rois
 * For Gene reference and gene x
 */
package RNA_Scope;


import static RNA_Scope_Utils.RNA_Scope_Processing.closeImages;
import static RNA_Scope_Utils.RNA_Scope_Processing.dialog;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.io.File;
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


/**
 *
 * @author phm
 */
public class RNA_Scope_Roi implements PlugIn {

private String imageDir = "";
private String outDirResults = "";
private final Calibration cal = new Calibration();   


    /*
    * Get sum of intensity in stack
    */
    public static double StackIntensity(ImagePlus img) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(ZProjector.SUM_METHOD);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
        ImagePlus imgProj = zproject.getProjection();
        ImageProcessor imp = imgProj.getProcessor();
        double bgInt = imp.getStats().mean * imp.getStats().area;
        System.out.println("Mean Background = " + bgInt);
        closeImages(imgProj);
        return(bgInt);  
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
            ArrayList<String> ch = new ArrayList<>();
            for (int i = 0; i < imageFile.length; i++) {
                // Find nd files
                if (imageFile[i].endsWith(imageExt)) {
                    String imageName = inDir+ File.separator+imageFile[i];
                    rootName = imageFile[i].replace("."+ imageExt, "");
                    reader.setId(imageName);
                    int sizeZ = reader.getSizeZ();
                    imageNum++;
                    boolean showCal = false;
                    String channelsID = meta.getImageName(0);
                    if (!channelsID.contains("CSU"))
                       channelsID =  "CSU_405/CSU_488/CSU_561/CSU_642";
                    String[] channels = channelsID.replace("_", "-").split("/");
                    // Check calibration
                    if (imageNum == 1) {
                        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
                        cal.pixelHeight = cal.pixelWidth;
                        // problem to read calibration with nd files
                        if ((meta.getPixelsPhysicalSizeZ(0) == null))
                            showCal = true;
                        else
                            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
                        cal.setUnit("microns");
                        System.out.println("x/y cal = " +cal.pixelWidth+", z cal = " + cal.pixelDepth+", stack size = "+sizeZ);

                        // return the index for channels DAPI, Astro, Dots and ask for calibration if needed 
                        ch = dialog(channels, showCal, cal);

                        if (ch == null) {
                            IJ.showStatus("Plugin cancelled !!!");
                            return;
                        }
                    }
                    
                    // Find roi file name
                    String roiFile = inDir+ File.separator + rootName + ".zip";
                    if (!new File(roiFile).exists()) {
                        IJ.showStatus("No roi file found !") ;
                        return;
                    }
                    else {
                        double geneRefInt = 0, geneRefBgInt = 0, geneXInt = 0, geneXBgInt;
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
                        for (int r = 0; r< rm.getCount(); r++) {
                            Roi roi = rm.getRoi(r);
                            String roiName = roi.getName();
                            Rectangle rect = roi.getBounds();
                            Region reg = new Region(rect.x, rect.y, rect.width, rect.height);
                            options.setCropRegion(0, reg);
                            
                            // Open Gene reference channel
                            System.out.println("Opening reference gene channel ...");
                            ImagePlus imgGeneRef = BF.openImagePlus(options)[0];
                            if (roiName.contains("bg"))
                               geneRefBgInt = StackIntensity(imgGeneRef);
                            else
                               geneRefInt = StackIntensity(imgGeneRef);
                        }
                    }
                }
            }
        } catch (DependencyException | ServiceException | FormatException | IOException ex) {
        Logger.getLogger(RNA_Scope_Roi.class.getName()).log(Level.SEVERE, null, ex);
    }
    }

                    
                              
}
