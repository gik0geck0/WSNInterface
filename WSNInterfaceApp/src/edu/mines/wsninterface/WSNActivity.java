package edu.mines.wsninterface;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
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

		responseView = (ListView) findViewById(R.id.responses);
		responseAdapter = new PacketAdapter(this);
		responseView.setAdapter(responseAdapter);

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
}
