/* -*- compile-command: "find-and-gradle.sh inDeb"; -*- */
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

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.Random;

import org.eehouse.android.nbsplib.NBSProxy;
import org.eehouse.android.nbsp.ui.MainActivity;

public class RequestReceiver extends BroadcastReceiver {
    private static final String TAG = RequestReceiver.class.getSimpleName();

    @Override
    public void onReceive( Context context, Intent intent )
    {
        if ( intent != null
             && Intent.ACTION_SEND.equals(intent.getAction())
             && "text/nbsdata_tx".equals( intent.getType() ) ) {
            NBSProxy.CTRL cmd = NBSProxy.CTRL
                .values()[intent.getIntExtra( NBSProxy.EXTRA_CMD, -1 ) ];
            Log.d( TAG, "onReceive() got cmd: " + cmd );
            switch ( cmd ) {
            case REG:
                handleReg( context, intent );
                break;
            case SEND:
                handleSend( context, intent );
                break;
            }
        }
    }

    private void handleReg( Context context, Intent intent )
    {
        short port = intent.getShortExtra( NBSProxy.EXTRA_PORT, (short)-1 );
        String appID = intent.getStringExtra( NBSProxy.EXTRA_APPID );
        Log.d( TAG, "handleReg(" + port + ", " + appID + ")");
        PortReg.register( context, port, appID );
    }

    private boolean haveSendPermission( Context context )
    {
        String perm = Manifest.permission.SEND_SMS;
        boolean result = (ContextCompat.checkSelfPermission( context, perm )
                          == PackageManager.PERMISSION_GRANTED );
        Log.d( TAG, "haveSendPermission() => " + result );
        return result;
    }
    
    private void handleSend( Context context, Intent intent )
    {
        short port = intent.getShortExtra( NBSProxy.EXTRA_PORT, (short)-1 );
        if ( ! haveSendPermission( context ) ) {
            MainActivity.notifyNoPermissions( context, port );
        } else if ( !NBSProxy.isGSMPhone( context ) ) {
            MainActivity.notifyNotGSM( context );
        } else {
            try {
                String phone = intent.getStringExtra( NBSProxy.EXTRA_PHONE );
                String text = intent.getStringExtra( Intent.EXTRA_TEXT );
                byte[] data = Base64.decode( text, Base64.NO_WRAP );
                final int dataLen = data.length;

                SmsManager mgr = SmsManager.getDefault();
                PendingIntent sent = makeStatusIntent( context, R.string.msg_sent,
                                                       dataLen, port );
                PendingIntent delivery = makeStatusIntent( context, R.string.msg_delivered,
                                                           dataLen, port );
                mgr.sendDataMessage( phone, null, port, data, sent, delivery );
                Log.d( TAG, "sent " + data.length + " bytes to port "
                       + port + " on " + phone );

                StatsDB.record( context, true, port, data.length );
            } catch ( Exception ex ) {
                Log.e( TAG, "handleSend() got ex: " + ex.getMessage() );
                MainActivity.notifySendFailed( context );
            }
        }
    }

    private int mNextID = new Random().nextInt();

    private PendingIntent makeStatusIntent( Context context, int msgID,
                                            int len, short port )
    {
        Intent intent = new Intent( context.getString( msgID ) )
            .putExtra( NBSProxy.EXTRA_PORT, port )
            .putExtra( NBSProxy.EXTRA_DATALEN, len );
        return PendingIntent.getBroadcast( context, ++mNextID, intent, 0 );
                                           // PendingIntent.FLAG_UPDATE_CURRENT );
    }
}
