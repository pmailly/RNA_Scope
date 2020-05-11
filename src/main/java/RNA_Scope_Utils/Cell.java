/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package RNA_Scope_Utils;

/**
 *
 * @author phm
 */
public class Cell {
    // index
    private int index;
    // negative
    private boolean negative;
    // volume
    private double cellVol;
    // mean intensity in gene reference channel
    private double geneRefMeanInt;
    // integrated intensity in gene reference channel
    private double geneRefInt;
    // gene reference dot number
    private int geneRefDots;
    // mean dots volume
    private double geneRefMeanDotsVol;
    // integrated intensity of total gene reference dots
    private double geneRefDotsInt;
    // max integrated intensity of gene reference dots
    private double geneRefDotMaxInt;
    // integrated intensity in gene X channel
    private double geneXInt;
   
	
	public Cell(int index, boolean negative, double cellVol, double geneRefMeanInt, double geneRefInt, int geneRefDots, double geneRefMeanDotsVol,
                double geneRefDotsInt, double geneRefDotMaxInt, double geneXInt) {
            this.index = index;
            this.negative = negative;
            this.cellVol = cellVol;
            this.geneRefMeanInt = geneRefMeanInt;
            this.geneRefInt = geneRefInt;
            this.geneRefDots = geneRefDots;
            this.geneRefMeanDotsVol = geneRefMeanDotsVol;
            this.geneRefDotsInt = geneRefDotsInt;
            this.geneRefDotMaxInt = geneRefDotMaxInt;
            this.geneXInt = geneXInt;
	}
        
        public void setIndex(int index) {
            this.index = index;
	}
        
        public void setNegative(boolean negative) {
            this.negative = negative;
	}
        
        public void setCellVol(double cellVol) {
            this.cellVol = cellVol;
	}
        
        public void setGeneRefMeanInt(double geneRefMeanInt) {
            this.geneRefMeanInt = geneRefMeanInt;
	}
        
        public void setGeneRefInt(double geneRefInt) {
            this.geneRefInt = geneRefInt;
	}
        
        public void setGeneRefDots(int geneRefDots) {
            this.geneRefDots = geneRefDots;
        }
        
        public void setGeneRefMeanDotsVol(double geneRefMeanDotsVol) {
            this.geneRefMeanDotsVol = geneRefMeanDotsVol;
        }
        
        public void setGeneRefDotsInt(double geneRefDotsInt) {
            this.geneRefDotsInt = geneRefDotsInt;
        }
        
        public void setGeneRefDotsMaxInt(double geneRefDotMaxInt) {
            this.geneRefDotMaxInt = geneRefDotMaxInt;
        }
        
        public void setGeneXInt(double geneXInt) {
            this.geneXInt = geneXInt;
	}
        
        
        public int getIndex() {
            return index;
        }
        
        public boolean getNegative() {
            return negative;
        }
        
        public double getCellVol() {
            return cellVol;
        }
        
        public double getGeneRefMeanInt() {
            return geneRefMeanInt;
        }
        
        public double getGeneRefInt() {
            return geneRefInt;
	}
        
	public int getGeneRefDots() {
            return geneRefDots;
	}
        
        public double getGeneRefMeanDotsVol() {
            return geneRefMeanDotsVol;
        }
        
        public double getGeneRefDotsInt() {
            return geneRefDotsInt;
        }
        
        public double getGeneRefDotMaxInt() {
            return geneRefDotMaxInt;
        }
        
        public double getGeneXInt() {
            return geneXInt;
	}
        
}
