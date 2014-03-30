package edu.mines.wsninterface;

import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeAddress64;

public class NDResponse {
	// 16 bit address
	int my;

	// 64 bit address
	long serial;

	int rssi;
	String ni;
	
	NDResponse() {
		this.my = 0;
		this.serial = 0;
		this.rssi = 0;
		this.ni = null;
	}
	NDResponse(int my, long serial, int rssi, String ni) {
		this.my = my;
		this.serial = serial;
		this.rssi = rssi;
		this.ni = ni;
	}

	public int getMy() {
		return my;
	}
	
	public XBeeAddress16 getMYAddr() {
		return getMYAddr(my);
	}

	public long getSerial() {
		return serial;
	}

	public XBeeAddress64 getSerialAddr() {
		return getSerialAddr(serial);
	}

	public int getRssi() {
		return rssi;
	}

	public String getNi() {
		return ni;
	}

	public void setMy(int my) {
		this.my = my;
	}

	public void setSerial(long serial) {
		this.serial = serial;
	}

	public void setRssi(int rssi) {
		this.rssi = rssi;
	}

	public void setNi(String ni) {
		this.ni = ni;
	}
	
	@Override
	public String toString() {
		return ni.trim()
				+ "- MY:" + Integer.toHexString(my)
				// + " SA:" + Long.toHexString(serial)
				+ " " + rssi + "dBm";
	}
	
    public static XBeeAddress16 getMYAddr(int my) {
    	return new XBeeAddress16((my >> 8) & 0xFF, my & 0xFF);
    }

    public static XBeeAddress64 getSerialAddr(long serial) {
    	return new XBeeAddress64(
    			(int) ((serial >> 56) & 0xFF),
    			(int) ((serial >> 48) & 0xFF),
    			(int) ((serial >> 40) & 0xFF),
    			(int) ((serial >> 32) & 0xFF),
    			(int) ((serial >> 24) & 0xFF),
    			(int) ((serial >> 16) & 0xFF),
    			(int) ((serial >> 8 ) & 0xFF),
    			(int) ((serial >> 0 ) & 0xFF));
    }
}
