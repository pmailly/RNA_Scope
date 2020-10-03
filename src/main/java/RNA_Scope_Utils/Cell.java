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
    // volume in pixels
    private double cellVol;
    // volume in unit
    private double cellVolUnit;
    // Reference channel
    // mean intensity in gene reference channel
    private double geneRefMeanInt;
    // integrated intensity in gene reference channel
    private double geneRefInt;
    // gene reference dot number
    private int geneRefDots;
    // dots volume in pixels
    private double geneRefDotsVol;
    // dots volume in unit
    private double geneRefDotsVolUnit;
    // integrated intensity of total gene reference dots
    private double geneRefDotsInt;
    // Mean intensity of total gene reference dots
    private double geneRefDotsMeanInt;
    
    // Gene X channel
    // mean intensity in gene X channel
    private double geneXMeanInt;
    // integrated intensity in gene X channel
    private double geneXInt;
    // gene X dot number
    private int geneXDots;
    // dots volume in pixels
    private double geneXDotsVol;
    // dots volume in unit
    private double geneXDotsVolUnit;
    // integrated intensity of total gene X dots
    private double geneXDotsInt;
    // Mean intensity of gene X dots
    private double geneXDotsMeanInt;
   
	
	public Cell(int index, boolean negative, double cellVol, double cellVolUnit, double geneRefMeanInt, double geneRefInt, int geneRefDots, double geneRefDotsVol,
                double geneRefDotsVolUnit, double geneRefDotsInt, double geneRefDotMeanInt, double geneXMeanInt, double geneXInt, int geneXDots, double geneXDotsVol,
                double geneXDotsVolUnit, double geneXDotsInt, double geneXDotsMeanInt) {
            this.index = index;
            this.negative = negative;
            this.cellVol = cellVol;
            this.cellVolUnit = cellVolUnit;
            this.geneRefMeanInt = geneRefMeanInt;
            this.geneRefInt = geneRefInt;
            this.geneRefDots = geneRefDots;
            this.geneRefDotsVol = geneRefDotsVol;
            this.geneRefDotsVolUnit = geneRefDotsVolUnit;
            this.geneRefDotsInt = geneRefDotsInt;
            this.geneRefDotsMeanInt = geneRefDotMeanInt;
            this.geneXMeanInt = geneXMeanInt;
            this.geneXInt = geneXInt;
            this.geneXDots = geneXDots;
            this.geneXDotsVol = geneXDotsVol;
            this.geneXDotsVolUnit = geneXDotsVolUnit;
            this.geneXDotsInt = geneXDotsInt;
            this.geneXDotsMeanInt = geneXDotsMeanInt;
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
        
        public void setCellVolUnit(double cellVolUnit) {
            this.cellVolUnit = cellVolUnit;
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
        
        public void setGeneRefDotsVol(double geneRefDotsVol) {
            this.geneRefDotsVol = geneRefDotsVol;
        }
        
        public void setGeneRefDotsVolUnit(double geneRefDotsVolUnit) {
            this.geneRefDotsVolUnit = geneRefDotsVolUnit;
        }
        
        public void setGeneRefDotsInt(double geneRefDotsInt) {
            this.geneRefDotsInt = geneRefDotsInt;
        }
        
        public void setGeneRefDotsMeanInt(double geneRefDotsMeanInt) {
            this.geneRefDotsMeanInt = geneRefDotsMeanInt;
        }
        
        public void setGeneXMeanInt(double geneXMeanInt) {
            this.geneXMeanInt = geneXMeanInt;
	}
        
        public void setGeneXInt(double geneXInt) {
            this.geneXInt = geneXInt;
	}
        
        public void setGeneXDots(int geneXDots) {
            this.geneXDots = geneXDots;
        }
        
        public void setGeneXDotsVol(double geneXDotsVol) {
            this.geneXDotsVol = geneXDotsVol;
        }
        
        public void setGeneXDotsVolUnit(double geneXDotsVolUnit) {
            this.geneXDotsVolUnit = geneXDotsVolUnit;
        }
        
        public void setGeneXDotsInt(double geneXDotsInt) {
            this.geneXDotsInt = geneXDotsInt;
        }
        
        public void setGeneXDotsMeanInt(double geneXDotsMeanInt) {
            this.geneXDotsMeanInt = geneXDotsMeanInt;
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
        
        public double getCellVolUnit() {
            return cellVolUnit;
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
        
        public double getGeneRefDotsVol() {
            return geneRefDotsVol;
        }
        
        public double getGeneRefDotsVolUnit() {
            return geneRefDotsVolUnit;
        }
        
        public double getGeneRefDotsInt() {
            return geneRefDotsInt;
        }
        
        public double getGeneRefDotsMeanInt() {
            return geneRefDotsMeanInt;
        }
        
        public double getGeneXMeanInt() {
            return geneXMeanInt;
        }
        
        public double getGeneXInt() {
            return geneXInt;
	}
        
	public int getGeneXDots() {
            return geneXDots;
	}
        
        public double getGeneXDotsVol() {
            return geneXDotsVol;
        }
        
        public double getGeneXDotsVolUnit() {
            return geneXDotsVolUnit;
        }
        
        public double getGeneXDotsInt() {
            return geneXDotsInt;
        }
        
        public double getGeneXDotsMeanInt() {
            return geneXDotsMeanInt;
        }
        
}
