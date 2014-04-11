package edu.mines.wsninterface;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.util.Log;

import com.rapplogic.xbee.api.PacketListener;
import com.rapplogic.xbee.api.XBeeResponse;

public class ResponsePacketListener implements PacketListener {
	ArrayList<NDResponse> intermediateNodes;
	PacketNotification recipient;
	RxPacket incomplete_packet;
	
	ResponsePacketListener(PacketNotification recipient) {
		this.recipient = recipient;
		intermediateNodes = new ArrayList<NDResponse>();
	}

	@Override
	public void processResponse(XBeeResponse response) {
		// Example Packet
		// 00 0c 81 00 01 1c 02 68 65 6c 6c 6f 0d 0a 34
		// _____ Payload length
		// 		 __ Paylod Type (81 == Rx with 16bit address)
		// 		 	_____ source address
		// 00 0c 81 00 01 1c 02 68 65 6c 6c 6f 0d 0a 34
		// 				  __ RSSI Receive value
		// 				 	 __ +2 means PAN Broadcast +1 means address broadcast
		// 						____________________ Packet bytes (h e l l o \r \n)
		// 											 __ Checksum

		int[] rawBytes = response.getProcessedPacketBytes();
		// The payload describes everything after the header. Since I use the bytes directly, this is useless
		// int payloadLength = (rawBytes[0] << 8) + rawBytes[1];
		int payloadType = rawBytes[2];

		Log.d("WSNActivity", "Receive Packet: " + response);
		Log.d("WSNActivity", "Receive Raw Bytes (" + rawBytes.length + ") : " + PacketUtils.intsToHex(rawBytes));
		// System.out.println(rawBytes.length + " bytes, and Payload length: " + payloadLength);

		switch (payloadType) {

		// RxResponse: Could have a 16 bit addr OR a 64 bit addr
		case 0x80:
		case 0x81:
			if (response.getProcessedPacketBytes().length == RxPacket.MAX_PACKET_LENGTH) {
				Log.d("WSNInterface", "Found a packet of max length");
				if (incomplete_packet == null) {
					incomplete_packet = new RxPacket(response);
				} else if (incomplete_packet.merge(new RxPacket(response))) {
				}
			} else if (incomplete_packet != null && incomplete_packet.merge(new RxPacket(response))) {
				recipient.receivePacket(incomplete_packet);
				incomplete_packet = null;
			} else {
                // RxResponse with 64 bit address
                recipient.receivePacket(new RxPacket(response));
			}
			break;
		case 0x82:
			// Input line states with 64 bit address
			break;
		case 0x83:
			// Input line states with 16 bit address
			break;
		case 0x8a:
			// Modem status packet
			break;
		case 0x97:
			// Remote AT Response
			// 3 == Packet Type
			// [4,12) == 64 bit addr
			// [12,14) == 16 bit addr
			// [14,16) == AT Command Name
			// [16] == Status
			// [17,-1) == AT Payload
			recipient.receivePacket(new RxPacket(response));
					/*
					"<<RAT" + Integer.toHexString((rawBytes[12] << 8) + rawBytes[13]) + " "
					+ (char) rawBytes[14] + (char) rawBytes[15]
					+ ((rawBytes[16] == 0) ? "OK" : "ERROR")
					+ ((rawBytes.length > 17) ? PacketUtils.intsToHex(PacketUtils.arraySubstr(rawBytes, 17, -1)) : ""));
					*/
			break;

		case 0x88:
			// Local AT Response
			if ((char) rawBytes[4] == 'N' && (char) rawBytes[5] == 'D') {
				if (rawBytes[6] == 0)
					recipient.receivePacket(new RxPacket(response));
                /*
                 * ND: Node Discovery.
                 * Response format: MY, SH, SL, DB and NI
                 * 
                 * MY: 2-byte ID
                 * SH: first (highest) 32 bits of the serial number
                 * SL: last (lowest) 32 bits of serial
                 * DB: signal (dB) of last good packet
                 * NI: Node identifier string
                 * 
                 * 0001 0013 a200 40a9 a90d 2c 20 00
                 * ____ MY
                 * 		_________ SH
                 * 				  _________ SL
                 * 							__ DB
                 * 							   _____ NI (char*)
                 * 
                 * 
                 */
				// final ArrayList<NDResponse> foundNodes = new ArrayList<NDResponse>();
				// System.out.println("Raw Bytes: " + rawBytes.length);
				// System.out.println("Packet length: " + payloadLength);
				
				int byteIdx = 7;
				// last byte is the checksum

				// At the end of node discover, there's an empty ND packet
                if (byteIdx >= rawBytes.length-1) {
                	Log.d("WSNActivity", "Received the empty ND packet. Updating the target nodes");
                	recipient.foundNodesList(new ArrayList<NDResponse>(intermediateNodes));
                	intermediateNodes.clear();
                }

				while (byteIdx < rawBytes.length-1) {
					NDResponse node = new NDResponse();
					node.setMy((rawBytes[byteIdx]<<8) + rawBytes[byteIdx+1]);
					byteIdx+=2;
					
					byte[] serialBytes = new byte[8];
					serialBytes[0] = (byte) rawBytes[byteIdx+0];
					serialBytes[1] = (byte) rawBytes[byteIdx+1];
					serialBytes[2] = (byte) rawBytes[byteIdx+2];
					serialBytes[3] = (byte) rawBytes[byteIdx+3];
					serialBytes[4] = (byte) rawBytes[byteIdx+4];
					serialBytes[5] = (byte) rawBytes[byteIdx+5];
					serialBytes[6] = (byte) rawBytes[byteIdx+6];
					serialBytes[7] = (byte) rawBytes[byteIdx+7];
					node.setSerial(ByteBuffer.wrap(serialBytes).getLong());
					byteIdx+=8;

					node.setRssi(rawBytes[byteIdx]);
					byteIdx++;

					StringBuilder nibuilder = new StringBuilder();
					while (rawBytes[byteIdx] != 0) {
						nibuilder.append((char) rawBytes[byteIdx]);
						byteIdx++;
					}
					byteIdx++;
					node.setNi(nibuilder.toString());
					intermediateNodes.add(node);
					Log.d("WSNInterface", "Found node: " + node + " Serial: " + Long.toHexString(node.getSerial()));
				}

			} else {
				recipient.receivePacket(new RxPacket(response));
					/*
					"<<AT" + (char) rawBytes[4] + (char) rawBytes[5]
					+ ((rawBytes[7] == 0) ? " " : " ERROR ")	// Say nothing on success
					+ ((rawBytes.length > 8) ? PacketUtils.intsToHex(PacketUtils.arraySubstr(rawBytes, 8, -1)) : ""));
					*/
			}
			break;

		case 0x89:
			// TxResponse: Did the sending go OK?
			if (rawBytes[4] != 0) {
				recipient.receivePacket(new RxPacket(response));
					/*
					"<< TX Failed: "
					+ ((rawBytes[4] == 1) ? "No ACK received, and all retries exhausted"
						: (rawBytes[4] == 2) ? "CCA Failure"
							: (rawBytes[4] == 3) ? "Purged. Sleeping remote?"
								: "Unknown error"));
					*/
			}
			break;
		default:
			Log.d("WSNInterface", "Received a packet with an unknown packet type. Bytes: " + rawBytes);
		}
	}
}
