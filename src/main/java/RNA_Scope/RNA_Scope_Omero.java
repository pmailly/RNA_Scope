/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package RNA_Scope;

import static RNA_Scope.RNA_Scope.cal;
import static RNA_Scope.RNA_Scope.imageExt;
import static RNA_Scope.RNA_Scope.output_detail_Analyze;
import static RNA_Scope.RNA_Scope.removeSlice;
import RNA_Scope_Utils.Cell;
import static RNA_Scope_Utils.JDialogOmeroConnect.imageData;
import static RNA_Scope_Utils.JDialogOmeroConnect.selectedDataset;
import static RNA_Scope_Utils.JDialogOmeroConnect.selectedProject;
import RNA_Scope_Utils.OmeroConnect;
import static RNA_Scope_Utils.OmeroConnect.addFileAnnotation;
import static RNA_Scope_Utils.OmeroConnect.gateway;
import static RNA_Scope_Utils.OmeroConnect.getFileAnnotations;
import static RNA_Scope_Utils.OmeroConnect.getImageZ;
import static RNA_Scope_Utils.OmeroConnect.getResolutionImage;
import static RNA_Scope_Utils.OmeroConnect.securityContext;
import static RNA_Scope_Utils.RNA_Scope_Processing.dialog;
import static RNA_Scope_Utils.RNA_Scope_Processing.InitResults;
import static RNA_Scope_Utils.RNA_Scope_Processing.closeImages;
import static RNA_Scope_Utils.RNA_Scope_Processing.drawNegCells;
import static RNA_Scope_Utils.RNA_Scope_Processing.findGenePop;
import static RNA_Scope_Utils.RNA_Scope_Processing.findNucleus;
import static RNA_Scope_Utils.RNA_Scope_Processing.find_background;
import static RNA_Scope_Utils.RNA_Scope_Processing.find_negativeCell;
import static RNA_Scope_Utils.RNA_Scope_Processing.labelsObject;
import static RNA_Scope_Utils.RNA_Scope_Processing.colorPop;
import static RNA_Scope_Utils.RNA_Scope_Processing.readXML;
import static RNA_Scope_Utils.RNA_Scope_Processing.tagsCells;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.RoiManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
import mcib3d.image3d.ImageHandler;
import ome.model.units.BigResult;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.MetadataFacility;
import omero.gateway.model.ChannelData;
import omero.gateway.model.FileAnnotationData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import org.apache.commons.lang.ArrayUtils;
import org.xml.sax.SAXException;

/**
 *
 * @author phm
 */

 // Images on OMERO server

public class RNA_Scope_Omero implements PlugIn {
    
    String tempDir = System.getProperty("java.io.tmpdir");
    String outDirResults = tempDir+File.separator+"resulst.xls";
    private String imageExtension = ".nd";

    
    
    @Override
    public void run(String arg) {
        try {
            int imageNum = 0;
            ArrayList<String> ch = new ArrayList();
            // initialize results files
            InitResults(outDirResults);
            
            for (ImageData image : imageData) {
                if (image.getName().contains(imageExt)) {
                    PixelsData pixels = image.getDefaultPixels();
                    int sizeZ = pixels.getSizeZ();
                    int sizeC = pixels.getSizeC();
                    String rootName = image.getName().replace(imageExt, "");
                    MetadataFacility mf = gateway.getFacility(MetadataFacility.class);
                    String[] channels = new String[sizeC];
                    for(ChannelData chs : mf.getChannelData(securityContext, image.getId())) {
                        channels[chs.getIndex()] = chs.getChannelLabeling();
                    }

                    try {
                        imageNum++;
                        if (imageNum == 1) {
                            try {
                                double[] res = getResolutionImage(image);
                                cal.pixelWidth = res[0];
                                cal.pixelHeight = res[1];
                                cal.pixelDepth = res[2];
                            } catch (ExecutionException | BigResult ex) {
                                Logger.getLogger(RNA_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            // return the index for channels DAPI, Astro, Dots and ask for calibration if needed
                            ch = dialog(channels, false, cal);
                            if (ch == null) {
                                IJ.showStatus("Plugin cancelled !!!");
                                return;
                            }
                        }
                        int zStart = removeSlice;
                        int zStop = (sizeZ - 2*removeSlice) <= 0 ? sizeZ : sizeZ - removeSlice;

                        /*
                        * Open DAPI channel
                        */
                        int channelIndex = ArrayUtils.indexOf(channels, ch.get(0));
                        System.out.println("-- Opening Nucleus channel : "+ ch.get(0));
                        ImagePlus imgNuc = getImageZ(image, 1, channelIndex + 1, zStart, zStop).getImagePlus();


                        // find nucleus population
                        Objects3DPopulation cellsPop = findNucleus(imgNuc);

                        /*
                        * Open Channel 1 (gene reference)
                        */
                        channelIndex = ArrayUtils.indexOf(channels, ch.get(1));
                        System.out.println("-- Opening gene reference channel : "+ ch.get(1));
                        ImagePlus imgGeneRef = getImageZ(image, 1, channelIndex + 1, zStart, zStop).getImagePlus();

                        /*
                        * Open Channel 3 (gene X)
                        */
                        channelIndex = ArrayUtils.indexOf(channels, ch.get(2));
                        System.out.println("-- Opening gene X channel : " + ch.get(2));
                        ImagePlus imgGeneX = getImageZ(image, 1, channelIndex + 1, zStart, zStop).getImagePlus();

                        // Find gene reference dots
                        Objects3DPopulation geneRefDots = findGenePop(imgGeneRef);

                        // Find gene reference dots
                        Objects3DPopulation geneXDots = findGenePop(imgGeneX);
                        
                        // Find cells parameters in geneRef and geneX images
                        ArrayList<Cell> listCells = tagsCells(cellsPop, geneRefDots, geneXDots, imgGeneRef, imgGeneX);

                        // Estimated background in gene reference channel
                        double bgEstimated = find_background(imgGeneRef);

                        // find intensity in gene reference for negative cell

                        double[] negativeCellParams  = {0, 0};
                        if (!image.getAnnotations().isEmpty()) {
                            List<FileAnnotationData> fileAnnotations = getFileAnnotations(image, null);
                            // If exists roi in image
                            String roiFile = rootName + ".zip";
                            String xmlRoiFile = rootName + ".xml";
                            RoiManager rm = new RoiManager(false);
                            for (FileAnnotationData file : fileAnnotations) {
                                if (file.getFileName().equals(roiFile)) {
                                    roiFile = file.getFilePath();
                                    rm.reset();
                                    rm.runCommand("Open", roiFile);
                                    negativeCellParams  = find_negativeCell(rm, imgGeneRef, cellsPop, listCells);
                                }
                                else if (file.getFileName().equals(xmlRoiFile)) {
                                    xmlRoiFile = file.getFilePath();
                                    ArrayList<Point3D> ptsCell = readXML(xmlRoiFile);
                                    negativeCellParams = find_negativeCell(ptsCell, imgGeneRef, cellsPop, listCells);
                                }
                            }
                        }

                        // write results for each cell population
                        for (int n = 0; n < listCells.size(); n++) {
                            output_detail_Analyze.write(rootName+"\t"+listCells.get(n).getIndex()+"\t"+listCells.get(n).getCellVol()+"\t"+listCells.get(n).getNegative()
                                    +"\t"+listCells.get(n).getGeneRefInt()+"\t"+listCells.get(n).getGeneRefMeanInt()+"\t"+listCells.get(n).getGeneXInt()+"\t"+
                                    listCells.get(n).getGeneRefDots()+"\t"+listCells.get(n).getGeneRefMeanDotsVol()+"\t"+listCells.get(n).getGeneRefDotsInt()+"\t"+
                                    listCells.get(n).getGeneRefDotMaxInt()+"\t"+negativeCellParams[0]+"\t"+negativeCellParams[1]+"\t"+bgEstimated+"\n");
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

                        // Save images
                        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
                        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_Objects.tif");
                        imgCells.closeImagePlus();

                        // import  to Omero server
                        OmeroConnect.addImageToDataset(selectedProject, selectedDataset, outDirResults, rootName + "_Objects.tif", true);
                        new File(outDirResults + rootName + "_Objects.tif").delete();

                        // save random color nucleus popualation
                        ImagePlus imgColorPop = colorPop (cellsPop, imgNuc);
                        FileSaver ImgColorObjectsFile = new FileSaver(imgColorPop);
                        ImgColorObjectsFile.saveAsTiff(outDirResults + rootName + "_Nucleus-ColorObjects.tif");

                        // import to Omero server
                        OmeroConnect.addImageToDataset(selectedProject, selectedDataset, outDirResults, rootName + "_Nucleus-ColorObjects.tif", true);
                        new File(outDirResults + rootName + "_Nucleus-ColorObjects.tif").delete();

                        closeImages(imgNuc);
                        closeImages(imgGeneRef);
                        closeImages(imgGeneX);
                        closeImages(imgObjects);
                        closeImages(imgCellLabels);

                    } catch (DSOutOfServiceException | ExecutionException | DSAccessException | ParserConfigurationException | SAXException | IOException ex) {
                        Logger.getLogger(RNA_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (Exception ex) {
                        Logger.getLogger(RNA_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            output_detail_Analyze.close();
            
            // Attach results file to image
            File fileResults = new File(outDirResults);
            addFileAnnotation(imageData.get(0), fileResults, "text/csv", "Results");
            fileResults.delete();
        } catch (ExecutionException | DSAccessException | DSOutOfServiceException | IOException ex) {
            Logger.getLogger(RNA_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
}
