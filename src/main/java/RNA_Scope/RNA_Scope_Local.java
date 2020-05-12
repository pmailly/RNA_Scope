package RNA_Scope;

import static RNA_Scope.RNA_Scope.cal;
import static RNA_Scope.RNA_Scope.imageExt;
import static RNA_Scope.RNA_Scope.outDirResults;
import static RNA_Scope.RNA_Scope.output_detail_Analyze;
import static RNA_Scope.RNA_Scope.removeSlice;
import static RNA_Scope.RNA_Scope.rootName;
import RNA_Scope_Utils.Cell;
import static Omero_Utils.JDialogOmeroConnect.imagesFolder;
import static Omero_Utils.JDialogOmeroConnect.localImages;
import static RNA_Scope_Utils.RNA_Scope_Processing.*;
import ij.IJ;
import ij.ImagePlus;
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
        try {
            if (localImages) {
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
                            if ((meta.getPixelsPhysicalSizeZ(0) == null) || (cal.pixelWidth == 1))
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


                         // Find cells parameters in geneRef and geneX images
                        ArrayList<Cell> listCells = tagsCells(cellsPop, geneRefDots, imgGeneRef, imgGeneX);

                        // Estimated background in gene reference and gene X channel
                        double bgGeneRefEstimated = find_background(imgGeneRef);
                        double bgGeneXEstimated = find_background(imgGeneX);

                        // find intensity in gene reference for negative cell
                        String roiFile = inDir + File.separator+rootName + ".zip";
                        double[] negativeCellParams  = {0, 0};
                        if (new File(roiFile).exists()) {
                            // find rois
                            RoiManager rm = new RoiManager(false);
                            rm.runCommand("Open", roiFile);
                            negativeCellParams  = find_negativeCell(rm, imgGeneRef, cellsPop, listCells);
                        }
                        else {
                            String xmlroiFile = inDir + File.separator+rootName + ".xml";
                            if (new File(xmlroiFile).exists()) {
                               ArrayList<Point3D> ptsCell = readXML(xmlroiFile);
                               negativeCellParams = find_negativeCell(ptsCell, imgGeneRef, cellsPop, listCells);
                            }
                        }


                        // write results for each cell population
                        for (int n = 0; n < listCells.size(); n++) {
                            output_detail_Analyze.write(rootName+"\t"+listCells.get(n).getIndex()+"\t"+listCells.get(n).getCellVol()+"\t"+listCells.get(n).getNegative()
                                    +"\t"+listCells.get(n).getGeneRefInt()+"\t"+listCells.get(n).getGeneRefMeanInt()+"\t"+listCells.get(n).getGeneXInt()+"\t"+
                                    listCells.get(n).getGeneRefDots()+"\t"+listCells.get(n).getGeneRefMeanDotsVol()+"\t"+listCells.get(n).getGeneRefDotsInt()+"\t"+
                                    listCells.get(n).getGeneRefDotMaxInt()+"\t"+negativeCellParams[0]+"\t"+negativeCellParams[1]+"\t"+bgGeneRefEstimated+"\t"+bgGeneXEstimated+"\n");
                            output_detail_Analyze.flush();
                        }


                        // save image for objects population
                        // red geneRef , green geneX, blue nucDilpop
                        ImageHandler imgCells = ImageHandler.wrap(imgNuc).createSameDimensions();
                        imgCells.setCalibration(cal);
                        ImageHandler imgNegCells = ImageHandler.wrap(imgNuc).createSameDimensions();
                        imgNegCells.setCalibration(cal);
                        ImagePlus imgCellLabels = ImageHandler.wrap(imgNuc).createSameDimensions().getImagePlus();
                        // draw nucleus population
                        cellsPop.draw(imgCells, 255);
                        drawNegCells(cellsPop, imgNegCells);
                        labelsObject(cellsPop, imgCellLabels);
                        ImagePlus[] imgColors = {imgGeneRef, imgGeneX, imgCells.getImagePlus(),null,imgNegCells.getImagePlus(),null,imgCellLabels};
                        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
                        imgObjects.setCalibration(cal);
                        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");
                        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
                        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_Objects.tif"); 
                        imgCells.closeImagePlus();
                        // save random color nucleus popualation
                        ImagePlus imgColorPop = randomColorPop (cellsPop, imgNuc);
                        FileSaver ImgColorObjectsFile = new FileSaver(imgColorPop);
                        ImgColorObjectsFile.saveAsTiff(outDirResults + rootName + "_Nucleus-ColorObjects.tif");

                        closeImages(imgNuc);
                        closeImages(imgGeneRef);
                        closeImages(imgGeneX);
                        closeImages(imgObjects);
                        closeImages(imgCellLabels);
                    }
                }
                output_detail_Analyze.close();
            }
            else
                new RNA_Scope_Omero().run("");
           
        } catch (IOException | DependencyException | ServiceException | FormatException | ParserConfigurationException | SAXException ex) {
            Logger.getLogger(RNA_Scope_Local.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        IJ.showStatus("Process done ...");
    }
    
    
}
