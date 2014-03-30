package edu.mines.wsninterface;

public class PacketUtils {

	public static int[] bytesToInts(byte[] bytes) {
		int[] iarray = new int[bytes.length];
		for (int i = 0; i < bytes.length; i++) {
			iarray[i] = bytes[i];
		}
		return iarray;
	}

	public static byte[] intsToBytes(int[] ints) {
		byte[] barray = new byte[ints.length];
		for (int i = 0; i < ints.length; i++) {
			barray[i] = (byte) ints[i];
		}
		return barray;
	}

	public static char[] intsToChars(int[] ints) {
		char[] carray = new char[ints.length];
		for (int i = 0; i < ints.length; i++) {
			carray[i] = (char) ints[i];
		}
		return carray;
	}

	public static String intsToHex(int[] ints) {
		StringBuilder sb = new StringBuilder();
		for (int val : ints) {
			sb.append(Integer.toHexString(val) + " ");
		}
		return sb.toString();
	}

	public static int[] arraySubstr(int[] vals, int start, int end) {
		if (end < 0) {
			end = vals.length + end;
		}
		if (start < 0) {
			start = vals.length + start;
		}
		if (end > vals.length) {
			return null;
		}

		boolean reverse = false;
		if (start > end) {
			reverse = true;
			int temp = start;
			start = end;
			end = temp;
		}

		int[] substr = new int[end - start];
		for (int pull = start, put = 0; (!reverse && pull < end)
				|| (reverse && pull > end); pull += reverse ? -1 : 1, put++) {
			substr[put] = vals[pull];
		}
		return substr;
	}
}
