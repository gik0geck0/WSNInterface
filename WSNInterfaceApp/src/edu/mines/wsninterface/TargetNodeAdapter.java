package edu.mines.wsninterface;

import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import edu.mines.wsninterface.NDResponse;

public class TargetNodeAdapter extends BaseAdapter {

    private ArrayList<NDResponse> mListItems;
    private LayoutInflater mLayoutInflater;

    public TargetNodeAdapter(Context context) {
        mListItems = new ArrayList<NDResponse>();

        //get the layout inflater
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return mListItems.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    public void add(NDResponse node) {
        mListItems.add(node);
    }

    public void clear() {
        mListItems.clear();
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {

        //check to see if the reused view is null or not, if is not null then reuse it
        if (view == null) {
            view = mLayoutInflater.inflate(R.layout.list_item, null);
        }

        //get the string item from the position "position" from array list to put it on the TextView
        String stringItem = mListItems.get(position).toString();
        if (stringItem != null) {

            TextView itemName = (TextView) view.findViewById(R.id.list_item_text_view);

            if (itemName != null) {
                //set the item name on the TextView
                itemName.setText(stringItem);
            }
        }

        //this method must return the view corresponding to the data at the specified position.
        return view;

    }
}
