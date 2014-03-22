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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import com.rapplogic.xbee.XBeeConnection;
import com.rapplogic.xbee.api.AtCommand;
import com.rapplogic.xbee.api.InputStreamThread;
import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeAddress64;
import com.rapplogic.xbee.api.XBeeException;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.XBeeTimeoutException;
import com.rapplogic.xbee.api.wpan.RxResponse;
import com.rapplogic.xbee.api.wpan.RxResponse16;
import com.rapplogic.xbee.api.wpan.RxResponse64;
import com.rapplogic.xbee.api.wpan.TxRequest64;

public class WSNConsole extends IOIOConsoleApp {
	int[] data_in;
	int[] old;
	
	BlockingQueue<String> requestqueue;

	// Boilerplate main(). Copy-paste this code into any IOIOapplication.
	public static void main(String[] args) throws Exception {
		
		System.setProperty("ioio.SerialPorts", "/dev/IOIO0");
		Logger.getRootLogger().addAppender(new ConsoleAppender(new SimpleLayout(), "System.out"));
		Logger.getLogger(XBee.class).setLevel(Level.ERROR);
		Logger.getLogger(InputStreamThread.class).setLevel(Level.ERROR);
		new WSNConsole().go(args);
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
				System.out.println("Running setup");
				uart = ioio_.openUart(rx, tx, baud, parity, stopbits);
				input = uart.getInputStream();
				output = uart.getOutputStream();

				xbconn = new XBeeConnection() {
					@Override public OutputStream getOutputStream() {
						return output; }
					@Override public InputStream getInputStream() {
						try { System.out.println("Returning input stream with available: " + input.available());
						} catch (IOException e) { e.printStackTrace(); }
						return input; }
					@Override public void close() {
						try {
							input.close();
						} catch (IOException e) { e.printStackTrace(); } }
				};

				
				System.out.println("Making new XBee");
				xbee = new XBee();
				System.out.println("XBee made");
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
				System.out.println("Forking a thread");
				myThread.start();
				System.out.println("Thread forked");
				try {
					System.out.println("Initing new provider connection");
					xbee.initProviderConnection(xbconn);
				} catch (XBeeException e) {
					e.printStackTrace();
				}
				
				System.out.println("Setup Done");
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
							resp = xbee.sendSynchronous(new AtCommand(nextCommand.substring(2)));
							System.out.println("AT" + nextCommand.substring(2) + " << " + resp);
						} else {
							TxRequest64 txr = new TxRequest64(new XBeeAddress64(0x00, 0x13, 0xA2, 0x00, 0x40, 0xA9, 0xA9, 0x0D), bytesToInts(nextCommand.getBytes()));
							resp = xbee.sendSynchronous(txr);
							// Resp should contain the TX Response, indicating that the TX was successful
							System.out.println("Fulfilled? " + resp);
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
}