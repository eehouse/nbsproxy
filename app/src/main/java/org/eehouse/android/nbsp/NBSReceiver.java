/* -*- compile-command: "find-and-gradle.sh insXw4Deb"; -*- */
/*
 * Copyright 2019 by Eric House (xwords@eehouse.org).  All rights reserved.
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

public class NBSReceiver extends BroadcastReceiver {
    private static final String TAG = NBSReceiver.class.getSimpleName();

    @Override
    public void onReceive( Context context, Intent intent )
    {
        String action = intent.getAction();
        if ( action.equals("android.intent.action.DATA_SMS_RECEIVED") ) {
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
                            forward( context, phone, body );
                        } catch ( NullPointerException npe ) {
                            Log.e( TAG, "npe: " + npe.getMessage() );
                        }
                    }
                }
            }
        }
    }

    private void forward( Context context, String phone, byte[] data )
    {
        Log.d( TAG, "got " + data.length + " bytes from " + phone );

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream( data );
            DataInputStream dis = new DataInputStream( bais );
            int code = dis.readInt();
            String appID = getAppIDFor( context, code );
            int len = dis.available();
            data = new byte[len];
            dis.readFully( data );
            String asStr = Base64.encodeToString( data, Base64.NO_WRAP );

            Intent intent = new Intent()
                .setAction( Intent.ACTION_SEND )
                .putExtra( Intent.EXTRA_TEXT, asStr )
                .putExtra( "PHONE", phone )
                .setPackage( appID )
                .setType( "text/nbsdata" )
                ;
            context.startActivity( intent );
        } catch ( android.content.ActivityNotFoundException anfe ) {
            Log.e( TAG, "ActivityNotFoundException: " + anfe.getMessage() );
        } catch ( IOException ioe ) {
            Log.e( TAG, "ioe: " + ioe );
        }
    }

    private static Map<Integer, String> sMap = new HashMap<>();

    private String getAppIDFor( Context context, int code )
    {
        String result = sMap.get( code );
        if ( result == null ) {
            Log.d( TAG, "looking up appIDs" );
            PackageManager pm = context.getPackageManager();
            //get a list of installed apps.
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo pi : packages) {
                String appName = pi.packageName;
                int appCode = appName.hashCode();
                if ( code == appCode ) {
                    result = appName;
                    sMap.put( code, appName );
                    break;
                }
            }
            Log.d( TAG, "DONE looking up appIDs" );
        }
        return result;
    }
}
