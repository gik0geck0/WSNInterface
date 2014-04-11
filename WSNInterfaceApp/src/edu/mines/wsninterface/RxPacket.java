package edu.mines.wsninterface;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.util.Log;

import com.rapplogic.xbee.api.XBeeResponse;

public class RxPacket /* extends XBeeResponse */ {
	
	public static int MAX_PACKET_LENGTH = 108;	// 100 characters + 1 checksum + 3 header + 1 rssi + 1 flags + 2 address
	
	public static enum PacketType {
		Rx16Response,
		Rx64Response,
		Line16Input,
		Line64Input,
		ModemStatus,
		LocalATResponse,
		RemoteATResponse,
		TxResponse,
		UNKNOWN
		
		/*
		case 0x80:
			// RxResponse with 64 bit address
		case 0x81:
			// RxResponse with 16 bit address
		case 0x82:
			// Input line states with 64 bit address
		case 0x83:
			// Input line states with 16 bit address
		case 0x8a:
			// Modem status packet
		case 0x97:
			// Remote AT Response
		case 0x88:
			// Local AT Response
		case 0x89:
			// TxResponse: Did the sending go OK?
		default:
		*/
	}

	private String strRepr;
	private ArrayList<XBeeResponse> rawResponses;
	private PacketType packetType;

	RxPacket(XBeeResponse rxr) {
		this.rawResponses = new ArrayList<XBeeResponse>();
		rawResponses.add(rxr);

		int[] rawBytes = rxr.getProcessedPacketBytes();
		int payloadType = rawBytes[2];

		switch (payloadType) {
		case 0x80:
			packetType = PacketType.Rx64Response;
			// RxResponse with 64 bit address
			strRepr = "<< " 
					+ new String(PacketUtils.intsToBytes(PacketUtils.arraySubstr(rawBytes, 3, 11)))
					+ " (-" + rawBytes[11] + "dBm)"
					+ ": ";
					// + new String(PacketUtils.intsToBytes(PacketUtils.arraySubstr(rawBytes, 13, -1)));
			break;
			
		case 0x81:
			packetType = PacketType.Rx16Response;
			// RxResponse with 16 bit address
			strRepr = "<< " 
					+ (new String(PacketUtils.intsToBytes(PacketUtils.arraySubstr(rawBytes, 3, 5))).trim())
					+ " (-" + rawBytes[5] + "dBm)"
					+ ": ";
					// + new String(PacketUtils.intsToBytes(PacketUtils.arraySubstr(rawBytes, 7, -1)));
			break;
			
		case 0x82:
			packetType = PacketType.Line64Input;
			// Input line states with 64 bit address
			break;
		case 0x83:
			packetType = PacketType.Line16Input;
			// Input line states with 16 bit address
			break;
		case 0x8a:
			packetType = PacketType.ModemStatus;
			// Modem status packet
			break;
		case 0x97:
			packetType = PacketType.RemoteATResponse;
			// Remote AT Response
			// 3 == Packet Type
			// [4,12) == 64 bit addr
			// [12,14) == 16 bit addr
			// [14,16) == AT Command Name
			// [16] == Status
			// [17,-1) == AT Payload
			strRepr = "<<RAT" + Integer.toHexString((rawBytes[12] << 8) + rawBytes[13]) + " "
					+ (char) rawBytes[14] + (char) rawBytes[15]
					+ ((rawBytes[16] == 0) ? "OK" : "ERROR")
					+ ((rawBytes.length > 17) ? PacketUtils.intsToHex(PacketUtils.arraySubstr(rawBytes, 17, -1)) : "");
			break;

		case 0x88:
			packetType = PacketType.LocalATResponse;
			// Local AT Response
            strRepr = "<<AT" + (char) rawBytes[4] + (char) rawBytes[5]
                    + ((rawBytes[6] == 0) ? " " : " ERROR ")	// Say nothing on success
                    + ((rawBytes.length > 7) ? PacketUtils.intsToHex(PacketUtils.arraySubstr(rawBytes, 7, -1)) : "");
			break;

		case 0x89:
			packetType = PacketType.TxResponse;
			// TxResponse: Did the sending go OK?
			if (rawBytes[4] != 0) {
				strRepr = "<< TX Failed: "
					+ ((rawBytes[4] == 1) ? "No ACK received, and all retries exhausted"
						: (rawBytes[4] == 2) ? "CCA Failure"
							: (rawBytes[4] == 3) ? "Purged. Sleeping remote?"
								: "Unknown error");
			}
			break;
		default:
			packetType = PacketType.UNKNOWN;
			strRepr = "UNKNOWN PACKET";
		}
	}
	
	@Override
	public String toString() {
		if (packetType == PacketType.Rx16Response || packetType == PacketType.Rx64Response) {
			Log.d("WSNInterface", "Byte String: " + strRepr + PacketUtils.bytesToHex(getBinaryData()));
			return strRepr +  PacketUtils.bytesToHex(getBinaryData());
		}
		return strRepr.trim();
	}
	
	public PacketType getPacketType() {
		return packetType;
	}
	
	public ArrayList<XBeeResponse> getRawResponses() {
		return rawResponses;
	}
	
	/**
	 * Returns all the binary data. This excludes the headers and footers; payload only
	 * @return
	 */
	public byte[] getBinaryData() {
		int totallength = 0;
		for (XBeeResponse xbr : rawResponses) {
			totallength += xbr.getProcessedPacketBytes().length - 1 - ((packetType == PacketType.Rx64Response) ? 13 : 7);
		}

		ByteBuffer bb = ByteBuffer.allocate(totallength);
		
		// Go through all the responses, and add their bytes sequentially
		// Skip the packet headers (includes address), and don't include the checksums
		for (XBeeResponse xbr : rawResponses) {
			bb.put(PacketUtils.intsToBytes(
					PacketUtils.arraySubstr(
							xbr.getProcessedPacketBytes()
						  , (packetType == PacketType.Rx64Response) ? 13 : 7
						  , -1)));
		}
		
		return bb.array();
	}
	
	/**
	 * Merges the data of two packets together. This is used with large packets, and only guaranteed to work with RxPackets (16 or 64)
	 * @param other Another packet who's contents will be copied into this one
	 * @return True if the packets merge correctly (primarily, are of the same type)
	 */
	public boolean merge(RxPacket other) {
		if (other.packetType != this.packetType)
			return false;
		
		this.rawResponses.addAll(other.rawResponses);
		return true;
	}
}
