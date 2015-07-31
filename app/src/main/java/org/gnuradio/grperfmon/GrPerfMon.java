package org.gnuradio.grperfmon;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;


public class GrPerfMon extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void sendOnClick(View view) {
        Intent intent = new Intent(this, PerfMon.class);
        EditText hostNameText = (EditText) findViewById(R.id.hostNameEditText);
        EditText portNumberText = (EditText) findViewById(R.id.portNumEditText);
        EditText updatePeriodText = (EditText) findViewById(R.id.updatePeriodEditText);
        String hostName = hostNameText.getText().toString();
        String portNumber = portNumberText.getText().toString();
        String updatePeriod = updatePeriodText.getText().toString();

        intent.putExtra("org.gnuradio.grperfmon.hostname", hostName);
        intent.putExtra("org.gnuradio.grperfmon.portnumber", portNumber);
        intent.putExtra("org.gnuradio.grperfmon.updateperiod", updatePeriod);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
