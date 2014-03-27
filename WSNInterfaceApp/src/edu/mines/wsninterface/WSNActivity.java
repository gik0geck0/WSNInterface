package edu.mines.wsninterface;

import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;

import com.rapplogic.xbee.XBeeConnection;
import com.rapplogic.xbee.api.AtCommand;
import com.rapplogic.xbee.api.PacketListener;
import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress64;
import com.rapplogic.xbee.api.XBeeException;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.wpan.TxRequest64;

public class WSNActivity extends IOIOActivity implements PacketListener {
	private Thread xbnotifier;
	private boolean keepGoing = true;
	private EditText commandEntry;
	private Button sendButton;

	private BlockingQueue<String> requestqueue;

	private ListView responseView;
	private ArrayAdapter<String> responseAdapter;
	
	private Spinner targetSpinner;
	private ArrayAdapter<String> targetAdapter;

	private RadioButton isConnected;
	private Logger logger;

	/**
	 * Called when the activity is first created. Here we normally initialize
	 * our GUI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		logger = Logger.getLogger(getClass().getName());
		commandEntry = (EditText) findViewById(R.id.commandentry);
		sendButton = (Button) findViewById(R.id.send);
		isConnected = (RadioButton) findViewById(R.id.is_connected);
		
		targetSpinner = (Spinner) findViewById(R.id.target_spinner);
		targetAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);
		targetSpinner.setAdapter(targetAdapter);

		requestqueue = new ArrayBlockingQueue<String>(10);

		sendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				requestqueue.add(commandEntry.getText().toString());
			}
		});

		responseView = (ListView) findViewById(R.id.responses);
		responseAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);
		responseView.setAdapter(responseAdapter);

		logger.debug("onCreate finished!");
		Logger.getLogger(com.rapplogic.xbee.api.XBee.class).setLevel(Level.ALL);
		Logger.getLogger(com.rapplogic.xbee.api.InputStreamThread.class).setLevel(Level.ALL);
	}

	@Override
	public void onStop() {
		super.onStop();
		// Tell the xbee notifier thread to stop
		keepGoing = false;
	}

	/**
	 * This is the thread on which all the IOIO activity happens. It will be run
	 * every time the application is resumed and aborted when it is paused. The
	 * method setup() will be called right after a connection with the IOIO has
	 * been established (which might happen several times!). Then, loop() will
	 * be called repetitively until the IOIO gets disconnected.
	 */
	class Looper extends BaseIOIOLooper {
		private Uart uart;
		private int rxpin = 34;
		private int txpin = 35;
		private int baud = 57600;
		private Uart.Parity parity = Uart.Parity.NONE;
		private Uart.StopBits stopBits = Uart.StopBits.ONE;
		private InputStream input;
		private OutputStream output;

		private XBee xb;
		private XBeeConnection xbconn;

		/**
		 * Called every time a connection with IOIO has been established.
		 * Typically used to open pins.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException {
			Log.d("WSNInterface::Setup", "Starting IOIO setup");
			uart = ioio_.openUart(rxpin, txpin, baud, parity, stopBits);
			input = uart.getInputStream();
			output = uart.getOutputStream();
			Log.d("WSNInterface::Setup", "Uart Setup Complete!");

			// Link between the XBee API and the UART
			xbconn = new XBeeConnection() {
				@Override
				public OutputStream getOutputStream() {
					return output;
				}

				@Override
				public InputStream getInputStream() {
					return input;
				}

				@Override
				public void close() {
					try {
						input.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};

			Log.d("WSNInterface::Setup", "Making new XBee");
			xb = new XBee();
			Log.d("WSNInterface::Setup", "XBee made");
			xbnotifier = new java.lang.Thread(new Runnable() {
				@Override
				public void run() {
					while (keepGoing) {
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
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			});
			Log.i("WSNInterface::Setup", "Forking a thread");
			xbnotifier.start();
			Log.i("WSNInterface::Setup", "Thread forked");


			try {
				System.out.println("Initing new provider connection");
				xb.initProviderConnection(xbconn);
			} catch (XBeeException e) {
				e.printStackTrace();
			}

			xb.addPacketListener(WSNActivity.this);
			Log.d("HelloUart::Setup", "XBee API Setup Complete!");
			WSNActivity.this.runOnUiThread(new Runnable() {
				@Override public void run() {
					isConnected.setChecked(true); }});
		}

		/**
		 * Called repetitively while the IOIO is connected.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		public void loop() throws ConnectionLostException {
			try {
				// TODO: Wait till the button is pressed
				String nextCommand = requestqueue.take();
				if (nextCommand != null) {
					Log.d("WSNInterface", ">> " + nextCommand);
					addResponseToView(">> " + nextCommand);

					if (nextCommand.startsWith("AT")) {
						xb.sendAsynchronous(new AtCommand(nextCommand
								.substring(2)));
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
                        TxRequest64 txr = new TxRequest64(
                        // TODO: Get the address from the spinner
                        		new XBeeAddress64(0x00, 0x13, 0xA2, 0x00, 0x40, 0xA9, 0xA9, 0x0D),
                                        bytesToInts(nextCommand.getBytes()));
                        xb.sendAsynchronous(txr);
					}
				}
				// byte[] bs = {0x68, 0x69, 0x0D, 0x0A};
			} catch (XBeeException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

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

	/**
	 * A method to create our IOIO thread.
	 * 
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}

	private void addResponseToView(final String message) {
		WSNActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				responseAdapter.add(message);
				responseAdapter.notifyDataSetChanged();
			}
		});
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
				final ArrayList<NDResponse> foundNodes = new ArrayList<NDResponse>();
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
					logger.debug("Found node: " + node + " Serial: " + Long.toHexString(node.getSerial()));
				}
                if (!foundNodes.isEmpty()) {
				WSNActivity.this.runOnUiThread(new Runnable() {
					@Override public void run() {
						targetAdapter.clear();
						for (NDResponse nd : foundNodes) {
							targetAdapter.add(nd.toString());
						}
						targetAdapter.notifyDataSetChanged();
					}});
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
