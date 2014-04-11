package edu.mines.wsninterface;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
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

					dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface dialog, int which) {
							dialog.cancel();}});

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

}
