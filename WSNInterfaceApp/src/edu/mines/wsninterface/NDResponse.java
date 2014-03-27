package edu.mines.wsninterface;

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

	public long getSerial() {
		return serial;
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
}
