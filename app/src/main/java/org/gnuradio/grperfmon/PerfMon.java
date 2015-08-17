package org.gnuradio.grperfmon;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.androidplot.ui.SeriesRenderer;
import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BarRenderer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;

import org.gnuradio.grcontrolport.RPCConnection;
import org.gnuradio.grcontrolport.RPCConnectionThrift;

import java.lang.reflect.Array;
import java.text.FieldPosition;
import java.text.Format;
import java.text.MessageFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class PerfMon extends Activity {

    public class RunNetworkThread implements Runnable {

        private RPCConnection conn;
        private String mHost;
        private Integer mPort;
        private PerfMon mActivity;
        private Boolean mConnected;
        private Handler mHandler;

        RunNetworkThread(String host, Integer port, PerfMon activity) {
            this.mHost = host;
            this.mPort = port;
            this.mActivity = activity;
            this.mConnected = false;
        }

        public void run() {
            if(!mConnected) {
                Log.d("PerfMon", "Getting Connection");
                conn = new RPCConnectionThrift(mHost, mPort);
                mConnected = true;
                Looper.prepare();
                mHandler = new Handler();
                Log.d("PerfMon", "Got Connection");
            }

            mActivity.updateKnobs();
            mHandler.postDelayed(this, mActivity.getUpdatePeriod());
            Looper.loop();
        }

        public RPCConnection getConnection() {
            if(conn == null) {
                throw new IllegalStateException("connection not established");
            }
            return conn;
        }
    }

    public class MyHandler extends Handler {

        public PerfMon activity;

        MyHandler(PerfMon activity) {
            this.activity = activity;
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            ArrayList<String> arrayString = bundle.getStringArrayList("knobs");
            activity.updateScreen(arrayString);
        }

    }

    class PCBarFormatter extends BarFormatter {

        public PCBarFormatter(int fillColor, int borderColor) {
            super(fillColor, borderColor);
        }

        @Override
        public Class<? extends SeriesRenderer> getRendererClass() {
            return BarRenderer.class;
        }

        @Override
        public SeriesRenderer getRendererInstance(XYPlot plot) {
            return new BarRenderer(plot);
        }
    }


    private class XLabelFormatter extends Format {
        private String[] mLabels = {};
        private int mMaxStrLen;

        @Override
        public StringBuffer format(Object object, StringBuffer buffer, FieldPosition field)
        {
            int parsedInt =  Math.round(Float.parseFloat(object.toString()));
            if(parsedInt < 0)
                return buffer;
            String labelString = mLabels[parsedInt];
            buffer.append(labelString);
            return buffer;
        }

        @Override
        public Object parseObject(String string, ParsePosition position) {
            return java.util.Arrays.asList(mLabels).indexOf(string);
        }

        public void setLabels(List<String> newLables) {
            mMaxStrLen = newLables.get(0).length();
            mLabels = new String[newLables.size()];
            for (int i = 0; i < newLables.size(); i++) {
                mLabels[i] = newLables.get(i);
                if(newLables.get(i).length() > mMaxStrLen) {
                    mMaxStrLen = newLables.get(i).length();
                }
            }
        }

        public int getMaxStrLen() {
            return mMaxStrLen;
        }
    }

    private RunNetworkThread networkthread;
    private LinearLayout pcLayout;
    private MyHandler handler;
    private List<String> knobs;
    private Integer mUpdatePeriod;

    private XYPlot mPlot;
    private PCBarFormatter mFormatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perf_mon);

        Intent intent = getIntent();
        final String hostName = intent.getStringExtra("org.gnuradio.grperfmon.hostname");
        final String portNumber = intent.getStringExtra("org.gnuradio.grperfmon.portnumber");
        final String updatePeriod = intent.getStringExtra("org.gnuradio.grperfmon.updateperiod");

        final Integer port = Integer.parseInt(portNumber);
        mUpdatePeriod = Integer.parseInt(updatePeriod);
        Log.d("PerfMon", "Connecting to: " + hostName + ":" + port);

        mPlot = (XYPlot) findViewById(R.id.barXYPlot);
        mFormatter = new PCBarFormatter(Color.argb(200, 255, 105, 5), Color.LTGRAY);

        // reduce the number of range labels
        mPlot.setTicksPerRangeLabel(3);
        mPlot.setRangeLowerBoundary(0, BoundaryMode.FIXED);
        mPlot.getGraphWidget().setGridPadding(30, 10, 30, 0);
        mPlot.setTicksPerDomainLabel(2);

        knobs = new ArrayList<>();
        knobs.add(".*::work time");

        handler = new MyHandler(this);

        networkthread = new RunNetworkThread(hostName, port, this);

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(networkthread);

        //pcLayout = (LinearLayout) findViewById(R.id.pcLayout);
    }

    public void updateScreen(ArrayList<String> arrayString) {
        Double sum = 0.0;
        for (String s : arrayString) {
            Double val = Double.parseDouble(s.split("::")[1].split(": ")[1]);
            sum += val;
        }

        List<String>  keys = new ArrayList<>();
        List<Integer> vals = new ArrayList<>();
        for (String s : arrayString) {
            Double dVal = 100.0 * Double.parseDouble(s.split("::")[1].split(": ")[1]) / sum;
            Integer iVal = dVal.intValue();
            vals.add(iVal);

            keys.add(s.split("::")[0]);
        }

    	// Remove any series from the plot
        for (XYSeries elem : mPlot.getSeriesSet()) {
            mPlot.removeSeries(elem);
        }

        // Add scaled values to series
        XYSeries series = new SimpleXYSeries(vals,
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "");

        // Plot the per cent series

        mPlot.addSeries(series, mFormatter);
        BarRenderer renderer = ((BarRenderer)mPlot.getRenderer(BarRenderer.class));
        renderer.setBarRenderStyle(BarRenderer.BarRenderStyle.STACKED);
        renderer.setBarWidthStyle(BarRenderer.BarWidthStyle.FIXED_WIDTH);
        renderer.setBarWidth(50);
        renderer.setBarGap(1);

        XLabelFormatter labelFormatter = new XLabelFormatter();
        labelFormatter.setLabels(keys);
        int strlen = labelFormatter.getMaxStrLen();
        mPlot.setDomainStep(XYStepMode.INCREMENT_BY_VAL, 0.5);
        mPlot.setDomainValueFormat(labelFormatter);
        mPlot.getGraphWidget().setDomainLabelOrientation(-90f);
        mPlot.getGraphWidget().setDomainLabelVerticalOffset(-25f);
        mPlot.getGraphWidget().getDomainLabelPaint().setTextAlign(Paint.Align.RIGHT);
        mPlot.getGraphWidget().setMarginBottom(15f*strlen);
        mPlot.getGraphWidget().refreshLayout();

        mPlot.redraw();
    }

    public void updateScreenText(ArrayList<String> arrayString) {
        Double sum = 0.0;
        for (String s : arrayString) {
            Double val = Double.parseDouble(s.split("::")[1].split(": ")[1]);
            sum += val;
        }

        pcLayout.removeAllViews();
        for (String s : arrayString) {
            LinearLayout newline = new LinearLayout(this);
            newline.setOrientation(LinearLayout.HORIZONTAL);

            Double dVal = 100.0 * Double.parseDouble(s.split("::")[1].split(": ")[1]) / sum;
            Integer iVal = dVal.intValue();

            TextView viewKey = new TextView(this);
            TextView viewVal = new TextView(this);

            String vStr = "|";
            for (int i = 0; i < iVal; i++) {
                vStr += "-";
            }
            vStr += "|";

            viewKey.setText(s.split("::")[0] + ":    ");
            viewVal.setText(vStr);

            newline.addView(viewKey);
            newline.addView(viewVal);
            pcLayout.addView(newline);
        }
    }

    public void updateKnobs() {
        Message msg = handler.obtainMessage();
        Bundle bundle = new Bundle();

        Map<String, RPCConnection.KnobInfo> x = networkthread.getConnection().getRe(knobs);

        ArrayList<String> knobArray = new ArrayList<>();
        for (Map.Entry<String, RPCConnection.KnobInfo> e : x.entrySet()) {
            //Log.d("PerfMon", e.getKey() + ": " + e.getValue().value);
            String s = e.getKey() + ": " + e.getValue().value;
            knobArray.add(s);
        }

        bundle.putStringArrayList("knobs", knobArray);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    Integer getUpdatePeriod() {
        return mUpdatePeriod;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_perf_mon, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
