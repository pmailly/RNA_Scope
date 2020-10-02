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
public class Dot {
    private int index;
    private int volDot;
    private double bgIntDot;
    private double intDot;
    private double corIntDot;
    private int zMin;
    private int zMax;
    private int zCenter;
   
	
	public Dot(int index, int volDot, double bgIntDot, double intDot, double corIntDot, int zMin, int zMax, int zCenter) {
            this.index = index;
            this.volDot = volDot;
            this.bgIntDot = bgIntDot;
            this.intDot = intDot;
            this.corIntDot = corIntDot;
            this.zMin = zMin;
            this.zMax = zMax;
            this.zCenter = zCenter;
	}
        
        public void setIndex(int index) {
		this.index = index;
	}
        
        public void setVolDot(int volDot) {
		this.volDot = volDot;
	}
        
         public void setBgIntDot(double bgIntDot) {
		this.bgIntDot = bgIntDot;
	}
        
        public void setIntDot(double intDot) {
		this.intDot = intDot;
	}
        
        public void setCorIntDot(double corIntDot) {
		this.corIntDot = corIntDot;
	}
        
        public void setZmin(int zMin) {
		this.zMin = zMin;
	}
        
	public void setZmax(int zMax) {
		this.zMax = zMax;
	}
        
        public void setZCenter(int zCenter) {
		this.zCenter = zCenter;
	}
        
        public int getIndex() {
            return index;
        }
        
        public int getvolDot() {
            return volDot;
        }
        
        public double getBgIntDot() {
		return bgIntDot;
	}
        
	public double getIntDot() {
		return intDot;
	}
        
        public double getCorIntDot() {
		return corIntDot;
	}
        
	public int getZmin() {
		return zMin;
	}

	public int getZmax() {
		return zMax;
	}

        public int getZCenter() {
		return zCenter;
	}
}
