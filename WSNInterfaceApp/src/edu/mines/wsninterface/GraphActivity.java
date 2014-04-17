package edu.mines.wsninterface;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.SimpleXYSeries.ArrayFormat;
import com.androidplot.xy.XYPlot;

public class GraphActivity extends Activity /* implements OnTouchListener */ {
	public static String DATA = "data";
	public static String DATAFORMAT = "dataformat";
	
    private XYPlot mySimpleXYPlot;
    private SimpleXYSeries mySeries;
    private PointF minXY;
    private PointF maxXY;
    private Vector<Double> data;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_xy_plot);
		
		Intent i = getIntent();
		ArrayFormat dataformat = SimpleXYSeries.ArrayFormat.Y_VALS_ONLY;
		if (i.getExtras().containsKey(DATAFORMAT)) {
			dataformat = ArrayFormat.valueOf(i.getStringExtra(DATAFORMAT));
		}

		if (i.getExtras().containsKey(DATA)) {
			double[] xarr = i.getDoubleArrayExtra(DATA);
			data = new Vector<Double>(xarr.length);
			for (Double d : xarr) {
				data.add(d);
			}
		} else {
			data = new Vector<Double>();
		}

        mySimpleXYPlot = (XYPlot) findViewById(R.id.mySimpleXYPlot);
        // TODO: Zooming and panning not working yet
        // mySimpleXYPlot.setOnTouchListener(this);

      //Plot layout configurations
        /*
        mySimpleXYPlot.getGraphWidget().setTicksPerRangeLabel(1);
        mySimpleXYPlot.getGraphWidget().setTicksPerDomainLabel(1);
        mySimpleXYPlot.getGraphWidget().setRangeValueFormat(
                new DecimalFormat("#####.##"));
        mySimpleXYPlot.getGraphWidget().setDomainValueFormat(
                new DecimalFormat("#####.##"));
        mySimpleXYPlot.getGraphWidget().setRangeLabelWidth(25);
        mySimpleXYPlot.setRangeLabel("");
        mySimpleXYPlot.setDomainLabel("");
        */
        // mySimpleXYPlot.disableAllMarkup();
 
        //mySeries = new SimpleXYSeries(vector, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Series2");

        mySeries = new SimpleXYSeries(data, dataformat, "Series1");
 
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);
        mySimpleXYPlot.addSeries(mySeries, series1Format);

        mySimpleXYPlot.redraw();
        
        //Set of internal variables for keeping track of the boundaries
        mySimpleXYPlot.calculateMinMaxVals();
        minXY=new PointF(mySimpleXYPlot.getCalculatedMinX().floatValue(),mySimpleXYPlot.getCalculatedMinY().floatValue());
        maxXY=new PointF(mySimpleXYPlot.getCalculatedMaxX().floatValue(),mySimpleXYPlot.getCalculatedMaxY().floatValue());
	}

	@Override
	protected void onStart() {
		super.onStart();
	}
	 
	/*
	 * Theoretically, everything after this will allow for zooming and panning. I haven't gotten it to work though
	
    // Definition of the touch states
    static final int NONE = 0;
    static final int ONE_FINGER_DRAG = 1;
    static final int TWO_FINGERS_DRAG = 2;
    int mode = NONE;
 
    PointF firstFinger;
    float lastScrolling;
    float distBetweenFingers;
    float lastZooming;
 
    @Override
    public boolean onTouch(View arg0, MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN: // Start gesture
            firstFinger = new PointF(event.getX(), event.getY());
            mode = ONE_FINGER_DRAG;
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            //When the gesture ends, a thread is created to give inertia to the scrolling and zoom
            Timer t = new Timer();
                t.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        while(Math.abs(lastScrolling)>1f || Math.abs(lastZooming-1)<1.01){
                        lastScrolling*=.8;
                        scroll(lastScrolling);
                        lastZooming+=(1-lastZooming)*.2;
                        zoom(lastZooming);
                        mySimpleXYPlot.setDomainBoundaries(minXY.x, maxXY.x, BoundaryMode.AUTO);

                        mySimpleXYPlot.redraw();
                        /*
                        try {
                            // mySimpleXYPlot.postRedraw();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        * /
                        // the thread lives until the scrolling and zooming are imperceptible
                    }
                    }
                }, 0);
 
        case MotionEvent.ACTION_POINTER_DOWN: // second finger
            distBetweenFingers = spacing(event);
            // the distance check is done to avoid false alarms
            if (distBetweenFingers > 5f) {
                mode = TWO_FINGERS_DRAG;
            }
            break;
        case MotionEvent.ACTION_MOVE:
            if (mode == ONE_FINGER_DRAG) {
                PointF oldFirstFinger=firstFinger;
                firstFinger=new PointF(event.getX(), event.getY());
                lastScrolling=oldFirstFinger.x-firstFinger.x;
                scroll(lastScrolling);
                lastZooming=(firstFinger.y-oldFirstFinger.y)/mySimpleXYPlot.getHeight();
                if (lastZooming<0)
                    lastZooming=1/(1-lastZooming);
                else
                    lastZooming+=1;
                zoom(lastZooming);
                mySimpleXYPlot.setDomainBoundaries(minXY.x, maxXY.x, BoundaryMode.AUTO);
                mySimpleXYPlot.redraw();
 
            } else if (mode == TWO_FINGERS_DRAG) {
                float oldDist =distBetweenFingers;
                distBetweenFingers=spacing(event);
                lastZooming=oldDist/distBetweenFingers;
                zoom(lastZooming);
                mySimpleXYPlot.setDomainBoundaries(minXY.x, maxXY.x, BoundaryMode.AUTO);
                mySimpleXYPlot.redraw();
            }
            break;
        }
        return true;
    }
 
    private void zoom(float scale) {
        float domainSpan = maxXY.x    - minXY.x;
        float domainMidPoint = maxXY.x        - domainSpan / 2.0f;
        float offset = domainSpan * scale / 2.0f;
        minXY.x=domainMidPoint- offset;
        maxXY.x=domainMidPoint+offset;
    }
 
    private void scroll(float pan) {
        float domainSpan = maxXY.x    - minXY.x;
        float step = domainSpan / mySimpleXYPlot.getWidth();
        float offset = pan * step;
        minXY.x+= offset;
        maxXY.x+= offset;
    }
 
    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return FloatMath.sqrt(x * x + y * y);
    }
	 */
}
