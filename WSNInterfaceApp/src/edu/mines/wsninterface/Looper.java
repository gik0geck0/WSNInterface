package edu.mines.wsninterface;

import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.util.Log;

import com.rapplogic.xbee.XBeeConnection;
import com.rapplogic.xbee.api.AtCommand;
import com.rapplogic.xbee.api.RemoteAtRequest;
import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeAddress64;
import com.rapplogic.xbee.api.XBeeException;
import com.rapplogic.xbee.api.wpan.TxRequest16;
import com.rapplogic.xbee.api.wpan.TxRequest64;

/**
 * This is the thread on which all the IOIO activity happens. It will be run
 * every time the application is resumed and aborted when it is paused. The
 * method setup() will be called right after a connection with the IOIO has
 * been established (which might happen several times!). Then, loop() will
 * be called repetitively until the IOIO gets disconnected.
 */
class Looper extends BaseIOIOLooper {
	
	// Thread that runs to pass data from the Uart object to the XBee-api object
	private Thread xbnotifier;
	private boolean keepGoing = true;
	public void stop() { keepGoing = false; }
	private PacketNotification parentPacketNotify;
	private SetupSuccessNotify setupSuccessNotify;

	private BlockingQueue<String> requestqueue;
	public void addRequest(String request) { requestqueue.add(request); }

	private XBeeAddress targetAddr;
	public void setTarget(XBeeAddress addr) {
		targetAddr = addr;
	}

	Looper(PacketNotification responseNotify, SetupSuccessNotify successNotify) {
		requestqueue = new ArrayBlockingQueue<String>(10);
		parentPacketNotify = responseNotify;
		setupSuccessNotify = successNotify;
	}

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
		this.xbnotifier = new java.lang.Thread(new Runnable() {
			@Override
			public void run() {
				while (Looper.this.keepGoing) {
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
		this.xbnotifier.start();
		Log.i("WSNInterface::Setup", "Thread forked");


		try {
			System.out.println("Initing new provider connection");
			xb.initProviderConnection(xbconn);
		} catch (XBeeException e) {
			e.printStackTrace();
		}

		xb.addPacketListener(new ResponsePacketListener(this.parentPacketNotify));
		Log.d("HelloUart::Setup", "XBee API Setup Complete!");
		setupSuccessNotify.setupComplete();
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
			String nextCommand = this.requestqueue.take();
			if (nextCommand != null) {
				Log.d("WSNInterface", ">> " + nextCommand);
				parentPacketNotify.sendStringPacket(">> " + nextCommand);

				if (nextCommand.startsWith("AT")) {
					xb.sendAsynchronous(new AtCommand(nextCommand
							.substring(2)));
                } else if (nextCommand.startsWith("RAT")) {
                    // Send a remote AT Command to the selected MY
                	if (targetAddr instanceof XBeeAddress16) {
                		xb.sendAsynchronous(new RemoteAtRequest(
                    		(XBeeAddress16) targetAddr,
                            nextCommand.substring(7)));
                	} else {
                		xb.sendAsynchronous(new RemoteAtRequest(
                				(XBeeAddress64) targetAddr,
                				nextCommand.substring(7)));
                	}
				} else {
					if (targetAddr instanceof XBeeAddress16) {
                            xb.sendAsynchronous(new TxRequest16(
                                (XBeeAddress16) targetAddr,
                                PacketUtils.bytesToInts(nextCommand.getBytes())));
					} else {
						xb.sendAsynchronous(new TxRequest64(
								(XBeeAddress64) targetAddr,
                                PacketUtils.bytesToInts(nextCommand.getBytes())));
					}
				}
			}
		} catch (XBeeException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}