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
public class Nucleus {
    private int index;
    private double volNuc;
    private int mRNA;
    private double mRNAVol;
    private double mRNAColoc;
    private double mRNAInt;
   
	
	public Nucleus(int index, double volNuc, int mRNA, double mRNAColoc, double mRNAVol, double mRNAInt) {
            this.index = index;
            this.volNuc = volNuc;
            this.mRNA = mRNA;
            this.mRNAColoc = mRNAColoc;
            this.mRNAVol = mRNAVol;
            this.mRNAInt = mRNAInt;
	}
        
        public void setIndex(int index) {
		this.index = index;
	}
        
        public void setVolNuc(double volNuc) {
		this.volNuc = volNuc;
	}
        
        public void setmRNA(int mRNA) {
		this.mRNA = mRNA;
	}
        
        public void setmRNAColoc(double mRNAColoc) {
		this.mRNAVol = mRNAColoc;
	}
        
	public void setmRNAVol(double mRNAVol) {
		this.mRNAVol = mRNAVol;
	}
        
        public void setmRNAInt(double mRNAInt) {
		this.mRNAInt = mRNAInt;
	}
        
        public int getIndex() {
            return index;
        }
        
        public double getvolNuc() {
            return volNuc;
        }
        
	public int getmRNA() {
		return mRNA;
	}
        
	public double getmRNAColoc() {
		return mRNAColoc;
	}

	public double getmRNAVol() {
		return mRNAVol;
	}
        
        public double getmRNAInt() {
		return mRNAInt;
	}
}
