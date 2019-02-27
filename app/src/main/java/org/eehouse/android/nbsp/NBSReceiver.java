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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eehouse.android.nbsplib.NBSProxy;

public class NBSReceiver extends BroadcastReceiver {
    private static final String TAG = NBSReceiver.class.getSimpleName();
    private static final Pattern sPortPat = Pattern.compile("^sms://localhost:(\\d+)$");

    @Override
    public void onReceive( Context context, Intent intent )
    {
        String action = intent.getAction();
        if ( action.equals("android.intent.action.DATA_SMS_RECEIVED") ) {
            short port = getPort( context, intent );
            Bundle bundle = intent.getExtras();
            if ( null != bundle ) {
                Object[] pdus = (Object[])bundle.get( "pdus" );
                SmsMessage[] smses = new SmsMessage[pdus.length];

                for ( int ii = 0; ii < pdus.length; ++ii ) {
                    SmsMessage sms = SmsMessage.createFromPdu((byte[])pdus[ii]);
                    if ( null != sms ) {
                        try {
                            String phone = sms.getOriginatingAddress();
                            byte[] body = sms.getUserData();
                            forward( context, port, phone, body );
                        } catch ( NullPointerException npe ) {
                            Log.e( TAG, "npe: " + npe.getMessage() );
                        }
                    }
                }
            }
        }
    }

    private short getPort( Context context, Intent intent )
    {
        short result = 0;
        Matcher matcher = sPortPat.matcher( intent.getDataString() );
        if ( matcher.find() ) {
            result = Short.valueOf( matcher.group(1) );
            short expectPort = getConfiguredPort( context );
        }
        return result;
    }

    private void forward( final Context context, final short port,
                          final String phone, final byte[] clientData )
    {
        Log.i( TAG, "got " + clientData.length + " bytes from " + phone
               + " on port " + port );

        PortReg.lookup( context, port, new PortReg.OnHaveAppIDs() {
                @Override
                // this will be run in the DB's thread!
                public void haveAppIDs( String[] appIDs ) {
                    if ( appIDs == null || appIDs.length == 0 ) {
                        Log.e( TAG, "no app registered for port " + port );
                    } else {
                        String asStr = Base64.encodeToString( clientData,
                                                              Base64.NO_WRAP );
                        for ( String appID : appIDs ) {
                            try {
                                Intent intent = new Intent()
                                    .setAction( Intent.ACTION_SEND )
                                    .putExtra( Intent.EXTRA_TEXT, asStr )
                                    .putExtra( NBSProxy.EXTRA_PHONE, phone )
                                    .putExtra( NBSProxy.EXTRA_PORT, port )
                                    .setPackage( appID )
                                    .setType( "text/nbsdata_rx" )
                                    ;
                                context.sendBroadcast( intent );
                            } catch ( android.content.ActivityNotFoundException anfe ) {
                                Log.e( TAG, "ActivityNotFoundException: "
                                       + anfe.getMessage() );
                            }
                        }
                        StatsDB.record( context, false, port, clientData.length );
                    }
                }
            } );
    }

    private static Short sPort;
    private short getConfiguredPort( Context context )
    {
        if ( sPort == null ) {
            sPort = Short.valueOf(context.getString( R.string.nbsp_port ) );
        }
        return sPort;
    }
}
