/* -*- compile-command: "find-and-gradle.sh insXw4Deb"; -*- */
/*
 * Copyright 2019 by Eric House (eehouse@eehouse.org).  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.eehouse.android.nbsp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.Arrays;

public class RequestReceiver extends BroadcastReceiver {
    private static final String TAG = RequestReceiver.class.getSimpleName();

    @Override
    public void onReceive( Context context, Intent intent )
    {
        if ( intent != null
             && Intent.ACTION_SEND.equals(intent.getAction())
             && "text/nbsdata".equals( intent.getType() ) ) {
            short port = Short.valueOf( context.getString( R.string.nbs_port ) );
            String phone = intent.getStringExtra( "PHONE" );
            String appID = intent.getStringExtra( "APPID" );
            int code = appID.hashCode();
            String text = intent.getStringExtra( Intent.EXTRA_TEXT );
            byte[] data = Base64.decode( text, Base64.NO_WRAP );

            // The data we send will prepend the hashcode of the target
            // appID. Yeah. conflict's possible. Sue me. Once it happens.
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream( baos );
                dos.writeInt( code );
                dos.write( data, 0, data.length );
                dos.flush();
                data = baos.toByteArray();
            
                SmsManager mgr = SmsManager.getDefault();
                PendingIntent sent = null; // makeStatusIntent( MSG_SENT );
                PendingIntent delivery = null; // makeStatusIntent( MSG_DELIVERED );
                mgr.sendDataMessage( phone, null, port, data, sent, delivery );
                Log.d( TAG, "sent " + data.length + " bytes to port "
                       + port + " on " + phone );
            } catch ( IOException ioe ) {
                Log.e( TAG, "ioe: " + ioe );
            }
        }
    }
}
