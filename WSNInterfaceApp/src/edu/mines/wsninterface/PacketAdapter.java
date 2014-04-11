package edu.mines.wsninterface;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;

public class PacketAdapter extends BaseAdapter {
	ArrayList<RxPacket> packetList;
	ArrayAdapter<String> stringList;

	public PacketAdapter(Context context) {
		super();
		packetList = new ArrayList<RxPacket>();
		stringList = new ArrayAdapter<String>(context, R.layout.response_textview);
	}
	
	public void add(String s) {
		packetList.add(null);
		stringList.add(s);
	}
	
	public void add(RxPacket packet) {
		packetList.add(packet);
		stringList.add(packet.toString());
	}

	public void insert(String s, int i) {
		packetList.add(i, null);
		stringList.insert(s, i);
	}
	
	public void insert(RxPacket packet, int i) {
		packetList.add(i, packet);
		stringList.insert(packet.toString(), i);
	}
	
	public RxPacket getPacket(int i) {
		return packetList.get(i);
	}
	
	public void clear() {
		packetList.clear();
		stringList.clear();
	}

	@Override
	public int getCount() {
		return stringList.getCount();
	}

	@Override
	public Object getItem(int arg0) {
		return stringList.getItem(arg0);
	}

	@Override
	public long getItemId(int position) {
		return stringList.getItemId(position);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return stringList.getView(position, convertView, parent);
	}

}
