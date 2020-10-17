/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package RNA_Scope;

import static RNA_Scope.RNA_Scope.autoBackground;
import static RNA_Scope.RNA_Scope.cal;
import static RNA_Scope.RNA_Scope.deconv;
import static RNA_Scope.RNA_Scope.nucDil;
import static RNA_Scope.RNA_Scope.output_detail_Analyze;
import static RNA_Scope.RNA_Scope.removeSlice;
import static RNA_Scope.RNA_Scope.roiBgSize;
import static RNA_Scope.RNA_Scope.rootName;
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
import static RNA_Scope_Utils.RNA_Scope_Processing.findGenePop;
import static RNA_Scope_Utils.RNA_Scope_Processing.findNucleus;
import static RNA_Scope_Utils.RNA_Scope_Processing.findRoiBbackgroundAuto;
import static RNA_Scope_Utils.RNA_Scope_Processing.saveDotsImage;
import static RNA_Scope_Utils.RNA_Scope_Processing.saveCells;
import static RNA_Scope_Utils.RNA_Scope_Processing.saveCellsLabelledImage;
import static RNA_Scope_Utils.RNA_Scope_Processing.tagsCells;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.PlugIn;
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
                if (image.getName().endsWith(".nd") || image.getName().endsWith(".ics")) {
                        if (image.getName().endsWith(".nd")) {
                            rootName = image.getName().replace(".nd", "");
                            deconv = false;
                        }
                        else {
                            rootName = image.getName().replace(".ics", "");
                            deconv = true;
                        }
                    PixelsData pixels = image.getDefaultPixels();
                    int sizeZ = pixels.getSizeZ();
                    int sizeC = pixels.getSizeC();
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
                        * Open Channel 1 (gene reference)
                        */
                        int channelIndex = ArrayUtils.indexOf(channels, ch.get(1));
                        System.out.println("-- Opening gene reference channel : "+ ch.get(1));
                        ImagePlus imgGeneRef = getImageZ(image, 1, channelIndex + 1, zStart, zStop).getImagePlus();
                        
                        // Find gene reference dots
                        Objects3DPopulation geneRefDots = findGenePop(imgGeneRef);
                        System.out.println(geneRefDots.getNbObjects() + " gene dots ref found");
                        
                        /*
                        * Open Channel 3 (gene X)
                        */
                        channelIndex = ArrayUtils.indexOf(channels, ch.get(2));
                        System.out.println("-- Opening gene X channel : " + ch.get(2));
                        ImagePlus imgGeneX = getImageZ(image, 1, channelIndex + 1, zStart, zStop).getImagePlus();

                        // Find gene X dots
                        Objects3DPopulation geneXDots = findGenePop(imgGeneX);
                        System.out.println(geneXDots.getNbObjects() + " gene dots X found");
                        
                        // find background
                        Roi roiGeneRef = null, roiGeneX = null;
                        if (!autoBackground) {
                            // Find roi file for background
                            if (image.getAnnotations().isEmpty()) {
                                IJ.showStatus("No roi file found !");
                                return;
                            }
                            
                            List<FileAnnotationData> fileAnnotations = getFileAnnotations(image, null);
                            // If exists roi in image
                            String roiFile = rootName + ".zip";
                            RoiManager rm = new RoiManager(false);
                            for (FileAnnotationData file : fileAnnotations) {
                                if (file.getFileName().equals(roiFile)) {
                                    roiFile = file.getFilePath();
                                    rm.reset();
                                    rm.runCommand("Open", roiFile);
                                    for (int r = 0; r < rm.getCount(); r++) {
                                        Roi roi = rm.getRoi(r);
                                        if (roi.getName().equals("generef"))
                                            roiGeneRef = roi;
                                        else
                                            roiGeneX = roi;
                                    }
                                }
                            }
                        }
                        else {
                            // Estimated background in gene reference and gene X channel
                            roiGeneRef = findRoiBbackgroundAuto(imgGeneRef, roiBgSize);
                            roiGeneX = findRoiBbackgroundAuto(imgGeneX, roiBgSize);
                        }

                        /*
                        * Open DAPI channel
                        */
                        channelIndex = ArrayUtils.indexOf(channels, ch.get(0));
                        System.out.println("-- Opening Nucleus channel : "+ ch.get(0));
                        ImagePlus imgNuc = getImageZ(image, 1, channelIndex + 1, zStart, zStop).getImagePlus();


                        Objects3DPopulation cellsPop = new Objects3DPopulation();
                        // if no dilatation find cells with cellOutliner
                        if (nucDil != 0) {
                            cellsPop = findNucleus(imgNuc, imgGeneRef);
                        }
                        else
                            cellsPop = findNucleus(imgNuc, null);

                        // Find cells parameters in geneRef and geneX images
                        ArrayList<Cell> listCells = tagsCells(cellsPop, geneRefDots, geneXDots, imgGeneRef, imgGeneX, roiGeneRef, roiGeneX);


                        // write results for each cell population
                        for (int n = 0; n < listCells.size(); n++) {
                            output_detail_Analyze.write(rootName+"\t"+listCells.get(n).getIndex()+"\t"+listCells.get(n).getCellVol()+"\t"+listCells.get(n).getzCell()+"\t"+listCells.get(n).getCellGeneRefInt()
                                    +"\t"+listCells.get(n).getCellGeneRefBgInt()+"\t"+listCells.get(n).getnbGeneRefDotsCellInt()+"\t"+listCells.get(n).getGeneRefDotsVol()+"\t"+listCells.get(n).getGeneRefDotsInt()
                                    +"\t"+listCells.get(n).getnbGeneRefDotsSegInt()+"\t"+listCells.get(n).getCellGeneXInt()+"\t"+listCells.get(n).getCellGeneXBgInt()+"\t"+listCells.get(n).getnbGeneXDotsCellInt()
                                    +"\t"+listCells.get(n).getGeneXDotsVol()+"\t"+listCells.get(n).getGeneXDotsInt()+"\t"+listCells.get(n).getnbGeneXDotsSegInt()+"\n");
                            output_detail_Analyze.flush();                       

                        }

                        // Save labelled nucleus
                        saveCellsLabelledImage(imgNuc, cellsPop, imgGeneRef, imgGeneX, outDirResults, rootName);

                        // import  to Omero server
                        OmeroConnect.addImageToDataset(selectedProject, selectedDataset, outDirResults, rootName + "_Objects.tif", true);
                        new File(outDirResults + rootName + "_Objects.tif").delete();

                        // save random color nucleus popualation
                        saveCells(imgNuc, cellsPop, outDirResults, rootName);

                        // import to Omero server
                        OmeroConnect.addImageToDataset(selectedProject, selectedDataset, outDirResults, rootName + "_Nucleus-ColorObjects.tif", true);
                        new File(outDirResults + rootName + "_Nucleus-ColorObjects.tif").delete();
                        
                        // save dots segmentations
                        saveDotsImage (imgNuc, cellsPop, geneRefDots, geneXDots, outDirResults, rootName);
                        
                        // import to Omero server
                        OmeroConnect.addImageToDataset(selectedProject, selectedDataset, outDirResults, rootName + "_DotsObjects.tif", true);
                        new File(outDirResults + rootName + "_DotsObjects.tif").delete();

                        closeImages(imgNuc);
                        closeImages(imgGeneRef);
                        closeImages(imgGeneX);
                        

                    } catch (DSOutOfServiceException | ExecutionException | DSAccessException | ParserConfigurationException | SAXException | IOException ex) {
                        Logger.getLogger(RNA_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (Exception ex) {
                        Logger.getLogger(RNA_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            if (new File(outDirResults + "detailed_results.xls").exists())
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
