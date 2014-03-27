package edu.mines.mbuland;

import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOConnectionManager.Thread;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.pc.IOIOConsoleApp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ObjectInputStream.GetField;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import com.rapplogic.xbee.XBeeConnection;
import com.rapplogic.xbee.api.AtCommand;
import com.rapplogic.xbee.api.InputStreamThread;
import com.rapplogic.xbee.api.PacketListener;
import com.rapplogic.xbee.api.RemoteAtRequest;
import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeAddress64;
import com.rapplogic.xbee.api.XBeeException;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.XBeeTimeoutException;
import com.rapplogic.xbee.api.wpan.TxRequest64;

public class WSNConsole extends IOIOConsoleApp implements PacketListener {
	int[] data_in;
	int[] old;
	Logger logger;
	
	BlockingQueue<String> requestqueue;

	// Boilerplate main(). Copy-paste this code into any IOIOapplication.
	public static void main(String[] args) throws Exception {
		System.setProperty("ioio.SerialPorts", "/dev/IOIO0");
		Logger.getRootLogger().addAppender(new ConsoleAppender(new SimpleLayout(), "System.out"));
		Logger.getLogger(InputStreamThread.class).setLevel(Level.ERROR);
		Logger.getLogger("com.rapplogic.xbee.api").setLevel(Level.ERROR);
		Logger.getLogger(XBee.class).setLevel(Level.ERROR);
		new WSNConsole().go(args);
	}
	
	WSNConsole() {
		logger = Logger.getLogger(WSNConsole.class);
		logger.setLevel(Level.DEBUG);
	}

	@Override
	protected void run(String[] args) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				System.in));
		requestqueue = new ArrayBlockingQueue<String>(10);
		boolean abort = false;
		String line;
		while (!abort && (line = reader.readLine()) != null) {
			if (line.equals("q")) {
				abort = true;
			} else if (line.length() > 0) {
				requestqueue.add(new String(line));
			} else {
				System.out
						.println("Unknown input. t=toggle, n=on, f=off, q=quit.");
			}
		}
	}

	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) {
		return new BaseIOIOLooper() {			
			// Uart stuffs
			private Uart uart;
			private int rx = 34;
			private int tx = 35;
			private int baud = 57600;
			private Uart.Parity parity = Uart.Parity.NONE;
			private Uart.StopBits stopbits = Uart.StopBits.ONE;
			private InputStream input;
			private OutputStream output;
			
			private XBee xbee;
			private XBeeConnection xbconn;

			@Override
			protected void setup() throws ConnectionLostException,
					InterruptedException {
				logger.info("Running setup");
				uart = ioio_.openUart(rx, tx, baud, parity, stopbits);
				input = uart.getInputStream();
				output = uart.getOutputStream();

				xbconn = new XBeeConnection() {
					@Override public OutputStream getOutputStream() {
						return output; }
					@Override public InputStream getInputStream() {
						return input; }
					@Override public void close() {
						try {
							input.close();
						} catch (IOException e) { e.printStackTrace(); } }
				};

				
				logger.info("Making new XBee");
				xbee = new XBee();
				logger.info("XBee made");
				final java.lang.Thread myThread = new java.lang.Thread(new Runnable() {
					@Override
					public void run() {
						while (true) {
							try {
								if (input.available() > 0) {
									synchronized (xbconn) {
										xbconn.notify();
									}
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
							// Busy waiting... I know. I'm a terrible person
							try {
								//
								Thread.sleep(100);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							
						}
					}
				});
				logger.info("Forking a thread");
				myThread.start();
				logger.info("Thread forked");
				try {
					logger.info("Initing new provider connection");
					xbee.initProviderConnection(xbconn);
				} catch (XBeeException e) {
					e.printStackTrace();
				}
				xbee.addPacketListener(WSNConsole.this);
				
				logger.info("Setup Done");
			}

			@Override
			public void loop() throws ConnectionLostException,
					InterruptedException {

				XBeeResponse resp;
				try {
					String nextCommand = requestqueue.take();
					if (nextCommand != null) {
						/*
						 * ND: Node Discovery.
						 * Example
						 * 	 Format looks like:
						 * 	 MY    SH         SL         DB  NI
						 *   0001  0013 a200  40a9 a90d  2c  20  00
						 *   
						 *   MY: 2-byte ID
						 *   SH: first(highest) 32 bits of the serial number
						 *   SL: last(lowest) 32 bits of serial
						 *   DB: signal (dB) of last good packet
						 *   NI: Node identifier string
						 *   
						 *   DH: 0 if using MY as destination
						 *   		SH of target mote
						 *   DL: MY or SL of destination
						 */
						System.out.println(">> " + nextCommand);
						if (nextCommand.startsWith("AT")) {
							xbee.sendAsynchronous(new AtCommand(nextCommand.substring(2)));
							//System.out.println("AT" + nextCommand.substring(2) + " << " + resp);
						} else if (nextCommand.startsWith("RAT")) {
							// Send a remote AT Command to an MY
							// RAT0001MY	// Asks MY 0001 what it's MY is (silly, I know)
							String targetMY = nextCommand.substring(3, 7);
							xbee.sendAsynchronous(new RemoteAtRequest(
									new XBeeAddress16(
											Integer.parseInt(nextCommand.substring(3, 5)),
											Integer.parseInt(nextCommand.substring(5, 7))),
									nextCommand.substring(7)));
						} else {
							TxRequest64 txr = new TxRequest64(new XBeeAddress64(0x00, 0x13, 0xA2, 0x00, 0x40, 0xA9, 0xA9, 0x0D), bytesToInts(nextCommand.getBytes()));
							xbee.sendAsynchronous(txr);
							// Resp should contain the TX Response, indicating that the TX was successful
							/*
							XBeeResponse dataresp = xbee.getResponse();
							dataresp = xbee.getResponse();
							// 68 65 6c 6c 6f 0d 0a
							// h  e	 l  l  o  \r \n
							//
							// 0x02 in ASCII == Start of text
							// 00 0c 81 00 01 1c 02  68 65 6c 6c 6f 0d 0a 34
							// _____ Payload length
							//		 __ Paylod Type (81 == Rx with 16bit address)
							//			_____ source address
							// 00 0c 81 00 01 1c 02  68 65 6c 6c 6f 0d 0a 34
							//				  __ RSSI Receive value
							//					 __ +2 means PAN Broadcast +1 means address broadcast
							//						 ____________________ Packet bytes (h  e  l  l  o  \r \n)
							//											  __ Checksum
							RxResponse rxresp = (RxResponse) dataresp;
							int[] databytes = null;
							if (rxresp.getSourceAddress() instanceof XBeeAddress16) {
								RxResponse16 rxresp16 = (RxResponse16) rxresp;
								databytes = arraySubstr(rxresp16.getProcessedPacketBytes(), 7, -1);
							} else if (rxresp.getSourceAddress() instanceof XBeeAddress64) {
								RxResponse64 rxresp64 = (RxResponse64) rxresp;
								databytes = arraySubstr(rxresp64.getProcessedPacketBytes(),13, -1);
							} else {
								System.err.println("Unknown response! Was not RxResponse16 OR 64. Was: " + rxresp);
								return;
							}
							// System.out.println("bytes: " + intsToHex(databytes));
							System.out.println("<< " + new String(intsToBytes(databytes)).trim());
							*/
						}
					}
				} catch (XBeeTimeoutException e) {
					e.printStackTrace();
				} catch (XBeeException e) {
					e.printStackTrace();
				}
				
				// Thread.sleep(1000);
			}
		};
	}
	
	public static int[] bytesToInts(byte[] bytes) {
		int[] iarray = new int[bytes.length];
		for (int i=0; i<bytes.length; i++) {
			iarray[i] = bytes[i];
		}
		return iarray;
	}
	
	public static byte[] intsToBytes(int[] ints) {
		byte[] barray = new byte[ints.length];
		for (int i=0; i<ints.length; i++) {
			barray[i] = (byte) ints[i];
		}
		return barray;
	}
	
	public static char[] intsToChars(int[] ints) {
		char[] carray = new char[ints.length];
		for (int i=0; i<ints.length; i++) {
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
			end = vals.length + end; }
		if (start < 0) {
			start = vals.length + start; }
		if (end > vals.length) {
			return null; }
		
		boolean reverse = false;
		if (start > end) {
			reverse = true;
			int temp = start;
			start = end;
			end = temp;
		}
		
		int[] substr = new int[end-start];
		for (int pull=start, put=0; (!reverse && pull < end) || (reverse && pull > end); pull += reverse ? -1 : 1, put++) {
			substr[put] = vals[pull];
		}
		return substr;
	}
	
	private void addResponseToView(String message) {
		System.out.println(message);
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

		logger.debug("Receive Packet: " + response);
		logger.debug("Receive Raw Bytes: " + intsToHex(rawBytes));
		// System.out.println(rawBytes.length + " bytes, and Payload length: " + payloadLength);

		switch (payloadType) {

		case 0x80:
			// RxResponse with 64 bit address
			addResponseToView("<< " 
					+ arraySubstr(rawBytes, 3, 11)
					+ " (-" + rawBytes[11] + "dBm)"
					+ ": " 
					+ intsToHex(arraySubstr(rawBytes, 13, -1)));
			break;
			
		case 0x81:
			// RxResponse with 16 bit address
			addResponseToView("<< " 
					+ arraySubstr(rawBytes, 3, 5)
					+ " (-" + rawBytes[5] + "dBm)"
					+ ": " 
					+ intsToHex(arraySubstr(rawBytes, 7, -1)));
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
				addResponseToView("<<RAT" + Integer.toHexString((rawBytes[12] << 8) + rawBytes[13]) + " "
					+ (char) rawBytes[14] + (char) rawBytes[15]
					+ ((rawBytes[16] == 0) ? "OK" : "ERROR")
					+ ((rawBytes.length > 17) ? intsToHex(arraySubstr(rawBytes, 17, -1)) : ""));
			break;

		case 0x88:
			// Local AT Response
			if ((char) rawBytes[4] == 'N' && (char) rawBytes[5] == 'D') {
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
				ArrayList<NDResponse> foundNodes = new ArrayList<NDResponse>();
				// System.out.println("Raw Bytes: " + rawBytes.length);
				// System.out.println("Packet length: " + payloadLength);
				
				int byteIdx = 7;
				// last byte is the checksum
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
					foundNodes.add(node);
					System.out.println(node);
				}
			} else {
				addResponseToView("<<AT" + (char) rawBytes[4] + (char) rawBytes[5]
					+ ((rawBytes[7] == 0) ? " " : " ERROR ")	// Say nothing on success
					+ ((rawBytes.length > 8) ? intsToHex(arraySubstr(rawBytes, 8, -1)) : ""));
			}
			break;

		case 0x89:
			// TxResponse: Did the sending go OK?
			if (rawBytes[5] != 0) {
				addResponseToView("<< TX Failed: "
					+ ((rawBytes[5] == 1) ? "No ACK received, and all retries exhausted"
						: (rawBytes[5] == 2) ? "CCA Failure"
							: (rawBytes[5] == 3) ? "Purged. Sleeping remote?"
								: "Unknown error"));
			}
			break;
		default:
			logger.debug("Received a packet with an unknown packet type. Bytes: " + rawBytes);
		}
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

		logger.debug("Receive Packet: " + response);
		logger.debug("Receive Raw Bytes: " + intsToHex(rawBytes));
		// System.out.println(rawBytes.length + " bytes, and Payload length: " + payloadLength);

		switch (payloadType) {

		case 0x80:
			// RxResponse with 64 bit address
			addResponseToView("<< " 
					+ arraySubstr(rawBytes, 3, 11)
					+ " (-" + rawBytes[11] + "dBm)"
					+ ": " 
					+ intsToHex(arraySubstr(rawBytes, 13, -1)));
			break;
			
		case 0x81:
			// RxResponse with 16 bit address
			addResponseToView("<< " 
					+ arraySubstr(rawBytes, 3, 5)
					+ " (-" + rawBytes[5] + "dBm)"
					+ ": " 
					+ intsToHex(arraySubstr(rawBytes, 7, -1)));
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
				addResponseToView("<<RAT" + Integer.toHexString((rawBytes[12] << 8) + rawBytes[13]) + " "
					+ (char) rawBytes[14] + (char) rawBytes[15]
					+ ((rawBytes[16] == 0) ? "OK" : "ERROR")
					+ ((rawBytes.length > 17) ? intsToHex(arraySubstr(rawBytes, 17, -1)) : ""));
			break;

		case 0x88:
			// Local AT Response
			if ((char) rawBytes[4] == 'N' && (char) rawBytes[5] == 'D') {
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
				ArrayList<NDResponse> foundNodes = new ArrayList<NDResponse>();
				// System.out.println("Raw Bytes: " + rawBytes.length);
				// System.out.println("Packet length: " + payloadLength);
				
				int byteIdx = 7;
				// last byte is the checksum
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
					foundNodes.add(node);
					Log.d("WSNInterface", "Found node: " + node + " Serial: " + Long.toHexString(node.getSerial()));
				}
			} else {
				addResponseToView("<<AT" + (char) rawBytes[4] + (char) rawBytes[5]
					+ ((rawBytes[7] == 0) ? " " : " ERROR ")	// Say nothing on success
					+ ((rawBytes.length > 8) ? intsToHex(arraySubstr(rawBytes, 8, -1)) : ""));
			}
			break;

		case 0x89:
			// TxResponse: Did the sending go OK?
			if (rawBytes[5] != 0) {
				addResponseToView("<< TX Failed: "
					+ ((rawBytes[5] == 1) ? "No ACK received, and all retries exhausted"
						: (rawBytes[5] == 2) ? "CCA Failure"
							: (rawBytes[5] == 3) ? "Purged. Sleeping remote?"
								: "Unknown error"));
			}
			break;
		default:
			logger.debug("Received a packet with an unknown packet type. Bytes: " + rawBytes);
		}
	}
}
