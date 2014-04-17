package edu.mines.wsninterface;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.rapplogic.xbee.api.ApiId;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeePacketLength;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.wpan.RxResponse16;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class WSNActivity extends IOIOActivity implements PacketNotification, SetupSuccessNotify {
	private EditText commandEntry;
	private Button sendButton;

	private ListView responseView;
	private PacketAdapter responseAdapter;
	private Button refreshButton;
	
	private Looper ioioLooper;
	
	private Spinner targetSpinner;
    private TargetNodeAdapter targetAdapter;

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

		
		targetAdapter = new TargetNodeAdapter(this);
		targetSpinner = (Spinner) findViewById(R.id.target_spinner);
		targetSpinner.setAdapter(targetAdapter);
		targetSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View view, int position, long id) {
				Log.d("WSNActivity", "Selecting target node at position " + position + ": " + targetAdapter.getItem(position).getMYAddr() + " and setting for " + ioioLooper);
				ioioLooper.setTarget(targetAdapter.getItem(position).getMYAddr());
			}
			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				ioioLooper.setTarget(TargetNodeAdapter.BROADCASTRESPONSE.getMYAddr());
			}
		});

		sendButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				ioioLooper.addRequest(commandEntry.getText().toString());
				commandEntry.setText("");
			}
		});

		responseView = (ListView) findViewById(R.id.response_listview);
		responseAdapter = new PacketAdapter(this);

		RxResponse16 testData = new RxResponse16();
		testData.setApiId(ApiId.RX_16_RESPONSE);
		testData.setError(false);
		testData.setChecksum(0x00);
		testData.setLength(new XBeePacketLength(0x0E));
		testData.setOptions(0x01);
		testData.setRssi(0x30);
		testData.setData(new int[]{ 0x7e, 0x00, 0x81, 0x00, 0x01, 0x30, 0x01, 10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 25, 28, 31, 35, 40, 0x00 });
		testData.setRawPacketBytes(testData.getData());

		testData.setSourceAddress(new XBeeAddress16(0x00, 0x01));
		responseAdapter.add(new RxPacket(testData));
		responseView.setAdapter(responseAdapter);
		registerForContextMenu(responseView);

		refreshButton = (Button) findViewById(R.id.refresh);
		refreshButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				if (ioioLooper != null)
					ioioLooper.addRequest("ATND");
				else
					Toast.makeText(WSNActivity.this, "Cannot Refresh: Not Connected", Toast.LENGTH_SHORT).show();
			}});

		logger.debug("onCreate finished!");
		Logger.getLogger(com.rapplogic.xbee.api.XBee.class).setLevel(Level.ALL);
		Logger.getLogger(com.rapplogic.xbee.api.InputStreamThread.class).setLevel(Level.ALL);
	}

	@Override
	public void onStop() {
		super.onStop();
		// Tell the xbee notifier thread to stop
		ioioLooper.stop();
	}

	/**
	 * A method to create our IOIO thread.
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper() {
		if (ioioLooper == null)
			ioioLooper = new Looper(this, this);
		return ioioLooper;
	}

	void addResponseToView(final RxPacket message) {
		WSNActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				responseAdapter.insert(message, 0);
				responseAdapter.notifyDataSetChanged();
			}
		});
	}

	void addResponseToView(final String message) {
		WSNActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				responseAdapter.insert(message, 0);
				responseAdapter.notifyDataSetChanged();
			}
		});
	}
	
	@Override
	public void receivePacket(RxPacket packet) {
		addResponseToView(packet);
	}

	@Override
	public void receiveStringPacket(String packet) {
		addResponseToView(packet);
	}

	@Override
	public void sendStringPacket(String packet) {
		addResponseToView(packet);
	}

	@Override
	public void foundNodesList(final List<NDResponse> nodes) {
        WSNActivity.this.runOnUiThread(new Runnable() {
                @Override public void run() {
                        targetAdapter.clear();
                        for (NDResponse nd : nodes) {
                        	targetAdapter.add(nd);
                        }
                        targetAdapter.notifyDataSetChanged();
                        Toast.makeText(getApplicationContext(), "Target list Refreshed!", Toast.LENGTH_SHORT).show();
                }});
	}
	
	@Override
	public void setupComplete() {
		this.runOnUiThread(new Runnable() {
			@Override public void run() {
				WSNActivity.this.isConnected.setChecked(true); }});
	}
	
	// Long Press Menu
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		if (v.getId() == R.id.response_listview) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.packet_menu, menu);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.graph_data:
			// Step 1: Ask the user how the data should be interpreted
			// Binary-size of data: X = 1 bytes, Y= 0 bytes
			// Data format: Y_VALS_ONLY or XY_VALS_INTERLEAVED
			
			AlertDialog.Builder dataBuilder = new AlertDialog.Builder(this);
			dataBuilder.setNegativeButton("Cancel", cancelListener);
			
			LayoutInflater inflater = getLayoutInflater();
			View dialogLayout = inflater.inflate(R.layout.graph_opts, null);
			dataBuilder.setView(dialogLayout);
			final LinearLayout xlayout = (LinearLayout) dialogLayout.findViewById(R.id.x_layout);
			
			final Spinner formatSpinner = (Spinner) dialogLayout.findViewById(R.id.fmt_spinner);
			formatSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override public void onNothingSelected(AdapterView<?> arg0) {}

				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					// The selected item toggles visibility of the y-size layout
					// if (((String) parent.getItemAtPosition(position)).equals(getResources().getStringArray(R.array.data_format_choices)[1])) {
					if (position == 1) {
						xlayout.setVisibility(View.VISIBLE);
					} else {
						xlayout.setVisibility(View.GONE);
					}
				}
			});

			final EditText xwidth = (EditText) dialogLayout.findViewById(R.id.x_size);
			final EditText ywidth = (EditText) dialogLayout.findViewById(R.id.y_size);

			// Step 2: Graph it accordingly when the OK button is pressed
			dataBuilder.setPositiveButton("Graph!", new AlertDialog.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					Intent launchGraph = new Intent(WSNActivity.this, GraphActivity.class);

					// Check for blanks, and normalize to 1. However, if the chosen dataformat is Y_VALS_ONLY, then y will be 0
					if (xwidth.getText().length() == 0)
						xwidth.setText("1");

					if (ywidth.getText().length() == 0) {
						if (formatSpinner.getSelectedItemPosition() == 0)
							ywidth.setText("0");
						else
							ywidth.setText("1");
					}
								
					Log.d("WSNActivity", "Wrapping the data, then parsing");
					ByteBuffer dataBuff = ByteBuffer.wrap(responseAdapter.getPacket(info.position).getBinaryData());
					double[] data = parseData(dataBuff
							, Integer.parseInt(xwidth.getText().toString())
							, Integer.parseInt(ywidth.getText().toString()));
							
					launchGraph.putExtra(GraphActivity.DATA, data);
					launchGraph.putExtra(GraphActivity.DATAFORMAT, (String) formatSpinner.getSelectedItem());
					Log.d("WSNActivity", "Going to graph activity!");
					startActivity(launchGraph);
				}
			});
			
			dataBuilder.create().show();
			
			return true;
		case R.id.save_data:
			Log.d("WSNActivity", "Trying to save a file");
			// Save the bytes of this response into a file
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				Log.d("WSNActivity", "We have write access!!!");
				
				// Show a dialog, asking for simple info about how to save
				AlertDialog.Builder dialog = new AlertDialog.Builder(this);
				LinearLayout layout = new LinearLayout(this);
				layout.setOrientation(LinearLayout.VERTICAL);

				LinearLayout binaryLayout = new LinearLayout(this);
				binaryLayout.setOrientation(LinearLayout.HORIZONTAL);
				TextView binaryLabel = new TextView(this);
				binaryLabel.setText("Binary?");
				final CheckBox binaryOut = new CheckBox(this);
				binaryLayout.addView(binaryLabel);
				binaryLayout.addView(binaryOut);
				layout.addView(binaryLayout);

				LinearLayout hexLayout = new LinearLayout(this);
				hexLayout.setOrientation(LinearLayout.HORIZONTAL);
				TextView hexLabel = new TextView(this);
				hexLabel.setText("Hex?");
				final CheckBox hexOut = new CheckBox(this);
				hexLayout.addView(hexLabel);
				hexLayout.addView(hexOut);
				layout.addView(hexLayout);

				dialog.setView(layout);
				dialog.setTitle("Save as..");

				dialog.setNegativeButton("Cancel", cancelListener);

				dialog.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						Date now = new Date();
						File externaldir = Environment.getExternalStorageDirectory();
						File wsnDir = new File(externaldir.getAbsolutePath() + "/WSNData");
						
						// Make sure our output folder exists
						if (!wsnDir.exists()) {
							Log.d("WSNActivity", "Creating the WSNData folder at " + wsnDir.getAbsolutePath());
							wsnDir.mkdir();
						}

						if (hexOut.isChecked()) {
							// Create a new file that we can save the data to
							try {
								File outfile = new File(wsnDir.getAbsolutePath()
									+ "/datacapture_" + DateFormat.format("yyyy-MM-dd_HH:mm:ss.SSSZ", now) + ".txt");
								FileOutputStream fos = new FileOutputStream(outfile);

								// Write the content of this packet as a string (Header + Binary data)
								fos.write(responseAdapter.getPacket(info.position).toString().getBytes());
								fos.close();
								Log.d("WSNActivity", "Hex File was written!!");
							} catch (FileNotFoundException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						if (binaryOut.isChecked()) {
							try {
								// Create a new file that we can save the data to
								File outfile = new File(wsnDir.getAbsolutePath()
										+ "/datacapture_" + DateFormat.format("yyyy-MM-dd_HH:mm:ss.SSSZ", now) + ".dat");
								FileOutputStream fos = new FileOutputStream(outfile);

								// Write the content of this packet as binary data
								fos.write(responseAdapter.getPacket(info.position).getBinaryData());
								fos.close();
								Log.d("WSNActivity", "Binary File was written!!");
							} catch (FileNotFoundException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}});
				dialog.create().show();
			} else {
				Log.e("WSNActivity", "Cannot save the data. External dir not mounted with write access");
			}
			return true;
			default:
				return super.onContextItemSelected(item);
		}
	}
	
	public static double[] parseData(ByteBuffer bb, int xwidth, int ywidth) {
		int datapoints = bb.capacity() / (xwidth + ywidth) * (ywidth>0 ? 2 : 1);
		double[] darr = new double[datapoints];
		Log.d("WSNActivity", "Number of datapoints: " + datapoints);

		boolean isX = true;
		for (int i=0; i < datapoints; ++i) {
			Log.d("WSNActivity", "Datapoint " + i + "/" + datapoints);

			if (isX) {
				isX = false;
				if (xwidth == 0) {
					--i;
					continue;
				} else {
					switch (xwidth) {
						case 1:
						darr[i] = bb.get();
						break;
					case 2:
						darr[i] = bb.getShort();
						break;
					case 4:
						darr[i] = bb.getInt();
						break;
					case 8:
						darr[i] = bb.getLong();
						break;
					}
				}
			} else {
				isX = true;
				switch (ywidth) {
				case 1:
					darr[i] = bb.get();
					break;
				case 2:
					darr[i] = bb.getShort();
					break;
				case 4:
					darr[i] = bb.getInt();
					break;
				case 8:
					darr[i] = bb.getLong();
					break;
				}
			}
			Log.d("WSNActivity", "Datapoint " + i + ": " + darr[i]);
		}
		
		return darr;
	}
	
	// Defined here, since it's used in many places, and placing it out of the way is more ideal
	public static DialogInterface.OnClickListener cancelListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			dialog.cancel();
		}
	};
}
