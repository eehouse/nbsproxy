package org.eehouse.android.nbsp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate( Bundle sis )
    {
        super.onCreate(sis);
        sendIntent( getIntent() );
        finish();
    }

    private void sendIntent( Intent intent )
    {
        if ( intent != null
             && Intent.ACTION_SEND.equals(intent.getAction())
             && "text/nbsdata".equals( intent.getType() ) ) {
            short port = (short)Short.valueOf( getString( R.string.nbs_port ) );
            // String phone = intent.getStringExtra( "PHONE" );
            String phone = BuildConfig.TEST_PHONE;
            String text = intent.getStringExtra( Intent.EXTRA_TEXT );
            byte[] data = Base64.decode( text, Base64.NO_WRAP );
            int hash = intent.getIntExtra( "HASH", 0 );
            Log.d( TAG, "calculated hash: " + Arrays.hashCode(data)
                   + "; received hash: " + hash );
            for ( int ii = 0; ii < data.length; ++ii ) {
                byte byt = data[ii];
                Log.d( TAG, "got byte[" + ii + "]: " + byt );
            }

            SmsManager mgr = SmsManager.getDefault();
            PendingIntent sent = null; // makeStatusIntent( MSG_SENT );
            PendingIntent delivery = null; // makeStatusIntent( MSG_DELIVERED );
            mgr.sendDataMessage( phone, null, port, data, sent, delivery );
            Log.d( TAG, "sent " + data.length + " bytes to port "
                   + port + " on " + phone );
        }
    }
}
