package RNA_Scope;

/*
 * Find gene
 * 
 * Author Philippe Mailly
 */

import RNA_Scope_Utils.Cell;
import ij.*;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import RNA_Scope_Utils.JDialogOmeroConnect;
import static RNA_Scope_Utils.JDialogOmeroConnect.dialogCancel;
import static RNA_Scope_Utils.JDialogOmeroConnect.localImages;
import java.awt.Frame;


public class RNA_Scope implements PlugIn {

    public final boolean canceled = false;
    public static  String outDirResults = "";
    public static String rootName = "";
    public static Calibration cal = new Calibration();
    public static double minNucVol = 50;
    public static double maxNucVol = 900;
    public static float nucDil = 3;
    public static int removeSlice = 0;
    public static double singleDotIntGeneRef = 0;
    public static double singleDotIntGeneX = 0;
    public static boolean autoBackground = false;
    public static int roiBgSize = 150;
    public static int bgIndex = 5;
    private static final double pixWidth = 0.103;
    public static final double pixDepth = 0.5;
    public static boolean deconv = false;
    public static boolean ghostDots = false;
    public static Cell nucleus = new Cell(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static BufferedWriter output_detail_Analyze;
    

    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        if (canceled) {
            IJ.showMessage(" Pluging canceled");
            return;
        }
        JDialogOmeroConnect dialog = new JDialogOmeroConnect(new Frame(), true);
        dialog.show();
        if (dialogCancel){
            IJ.showStatus(" Pluging canceled");
            return;
        }

        /* 
        * Images on local machine
        */

        if (localImages) {
            new RNA_Scope_Local().run("");
        }
        
        /*
        Images on OMERO server
        */

        else {
            new RNA_Scope_Omero().run("");     
        }

        IJ.showStatus("Process done");
    }
}