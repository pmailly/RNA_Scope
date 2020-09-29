package RNA_Scope;

import static RNA_Scope.RNA_Scope.cal;
import static RNA_Scope.RNA_Scope.imageExt;
import static RNA_Scope.RNA_Scope.outDirResults;
import static RNA_Scope.RNA_Scope.output_detail_Analyze;
import static RNA_Scope.RNA_Scope.removeSlice;
import static RNA_Scope.RNA_Scope.rootName;
import RNA_Scope_Utils.Cell;
import static RNA_Scope_Utils.JDialogOmeroConnect.imagesFolder;
import static RNA_Scope_Utils.JDialogOmeroConnect.localImages;
import static RNA_Scope_Utils.RNA_Scope_Processing.*;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.BF;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.RoiManager;
import java.util.ArrayList;
import javax.xml.parsers.ParserConfigurationException;
import loci.plugins.in.ImporterOptions;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
import mcib3d.image3d.ImageHandler;
import org.apache.commons.lang.ArrayUtils;
import org.xml.sax.SAXException;


/*
 * Find nucleus with reference gene and/or virus 
 * based on objects segmentation of gene and virus channels
 * find mRNA in nucleus+ population
 * 
 */

/**
 *
 * @author phm
 */
public class RNA_Scope_Local implements PlugIn {
    
   
           
    @Override
    public void run(String arg) {
        if (localImages) {
            try {
                if (imagesFolder == null) {
                    return;
                }
                File inDir = new File(imagesFolder);
                String[] imageFile = inDir.list();
                if (imageFile == null) {
                    System.out.println("No Image found in "+imagesFolder);
                    return;
                }
                // create output folder
                outDirResults = inDir + File.separator+ "Out"+ File.separator;
                File outDir = new File(outDirResults);
                if (!Files.exists(Paths.get(outDirResults))) {
                    outDir.mkdir();
                }
                // initialize results files
                InitResults(outDirResults);
                
                // create OME-XML metadata store of the latest schema version
                ServiceFactory factory;
                factory = new ServiceFactory();
                OMEXMLService service = factory.getInstance(OMEXMLService.class);
                IMetadata meta = service.createOMEXMLMetadata();
                ImageProcessorReader reader = new ImageProcessorReader();
                reader.setMetadataStore(meta);
                Arrays.sort(imageFile);
                int imageNum = 0;
                ArrayList<String> ch = new ArrayList();
                for (int i = 0; i < imageFile.length; i++) {
                    // Find nd files
                    if (imageFile[i].endsWith(imageExt)) {
                        String imageName = inDir+ File.separator+imageFile[i];
                        rootName = imageFile[i].replace(imageExt, "");
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
                        
                        // find roi file for background detection
                        String roiFile = inDir+ File.separator + rootName + ".zip";
                        if (!new File(roiFile).exists()) {
                            IJ.showStatus("No roi file found !");
                            return;
                        }
                        // Find roi for gene ref and gene X
                        RoiManager rm = new RoiManager(false);
                        rm.runCommand("Open", roiFile);
                        Roi roiGeneRef = null, roiGeneX = null;
                        for (int r = 0; r < rm.getCount(); r++) {
                            Roi roi = rm.getRoi(r);
                            if (roi.getName().equals("generef"))
                                roiGeneRef = roi;
                            else
                                roiGeneX = roi;
                        }
                        reader.setSeries(0);
                        ImporterOptions options = new ImporterOptions();
                        options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                        options.setId(imageName);
                        options.setSplitChannels(true);
                        options.setZBegin(0, removeSlice);
                        if (2*removeSlice < sizeZ)
                            options.setZEnd(0, sizeZ-1  - removeSlice);
                        options.setZStep(0, 1);
                        
                        options.setQuiet(true);
                        
                        /*
                        * Open DAPI channel
                        */
                        int channelIndex = ArrayUtils.indexOf(channels, ch.get(0));
                        System.out.println("-- Opening Nucleus channel : "+ ch.get(0));
                        ImagePlus imgNuc = BF.openImagePlus(options)[channelIndex];
                        
                        // find nucleus population
                        Objects3DPopulation cellsPop = findNucleus(imgNuc);
                        
                        /*
                        * Open Channel 1 (gene reference)
                        */
                        channelIndex = ArrayUtils.indexOf(channels, ch.get(1));
                        System.out.println("-- Opening gene reference channel : "+ ch.get(1));
                        ImagePlus imgGeneRef = BF.openImagePlus(options)[channelIndex];
                        
                        /*
                        * Open Channel 3 (gene X)
                        */
                        channelIndex = ArrayUtils.indexOf(channels, ch.get(2));
                        System.out.println("-- Opening gene X channel : " + ch.get(2));
                        ImagePlus imgGeneX = BF.openImagePlus(options)[channelIndex];
                        
                        // Find gene reference dots
                        Objects3DPopulation geneRefDots = findGenePop(imgGeneRef);
                        
                        //Find gene X dots
                        Objects3DPopulation geneXDots = findGenePop(imgGeneX);
                        
                        // Find cells parameters in geneRef and geneX images
                        ArrayList<Cell> listCells = tagsCells(cellsPop, geneRefDots, geneXDots, imgGeneRef, imgGeneX);
                        
                        // Estimated background in gene reference and gene X channel
                        double bgGeneRefEstimated = find_background(imgGeneRef, roiGeneRef);
                        double bgGeneXEstimated = find_background(imgGeneX, roiGeneX);
                        
//                        // find intensity in gene reference for negative cell
//                        String roiFile = inDir + File.separator+rootName + ".zip";
//                        double[] negativeCellParams  = {0, 0};
//                        if (new File(roiFile).exists()) {
//                            // find rois
//                            RoiManager rm = new RoiManager(false);
//                            rm.runCommand("Open", roiFile);
//                            negativeCellParams  = find_negativeCell(rm, imgGeneRef, cellsPop, listCells);
//                        }
//                        else {
//                            String xmlroiFile = inDir + File.separator+rootName + ".xml";
//                            if (new File(xmlroiFile).exists()) {
//                               ArrayList<Point3D> ptsCell = readXML(xmlroiFile);
//                               negativeCellParams = find_negativeCell(ptsCell, imgGeneRef, cellsPop, listCells);
//                            }
//                        }


                        // write results for each cell population
                        for (int n = 0; n < listCells.size(); n++) {
                            output_detail_Analyze.write(rootName+"\t"+listCells.get(n).getIndex()+"\t"+listCells.get(n).getCellVol()+"\t"+listCells.get(n).getCellVolUnit()+"\t"+listCells.get(n).getNegative()
                                    +"\t"+listCells.get(n).getGeneRefInt()+"\t"+listCells.get(n).getGeneRefMeanInt()+"\t"+listCells.get(n).getGeneRefDots()
                                    +"\t"+listCells.get(n).getGeneRefDotsVol()+"\t"+listCells.get(n).getGeneRefDotsVolUnit()+"\t"+listCells.get(n).getGeneRefDotsInt()+"\t"+listCells.get(n).getGeneRefDotsMeanInt()
                                    +"\t"+listCells.get(n).getGeneXInt()+"\t"+listCells.get(n).getGeneXMeanInt()+"\t"+listCells.get(n).getGeneXDots()
                                    +"\t"+listCells.get(n).getGeneXDotsVol()+"\t"+listCells.get(n).getGeneXDotsVolUnit()+"\t"+listCells.get(n).getGeneXDotsInt()+"\t"+listCells.get(n).getGeneXDotsMeanInt()
                                    +"\t"+bgGeneRefEstimated+"\t"+bgGeneXEstimated+"\n");
                            output_detail_Analyze.flush();                       

                        }


                        // Save labelled nucleus
                        saveNucleusLabelledImage(imgNuc, cellsPop, imgGeneRef, imgGeneX, outDirResults, rootName);

                        // save random color nucleus popualation
                        saveNucleus(imgNuc, cellsPop, outDirResults, rootName);

                        // save dots segmented objects
                        saveDotsImage (imgNuc, cellsPop, geneRefDots, geneXDots, outDirResults, rootName);

                        closeImages(imgNuc);
                        closeImages(imgGeneRef);
                        closeImages(imgGeneX);
                    }
                }
                output_detail_Analyze.close();
                } catch (IOException | DependencyException | ServiceException | FormatException ex) {
                    Logger.getLogger(RNA_Scope_Local.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else
                new RNA_Scope_Omero().run("");

            IJ.showStatus("Process done ...");
        }
    
    
}
