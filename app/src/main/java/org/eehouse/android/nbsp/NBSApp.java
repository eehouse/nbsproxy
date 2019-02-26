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

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class NBSApp extends Application implements NBSProxy.OnReceived {
    private static final String TAG = NBSApp.class.getSimpleName();
    private static NBSProxy.OnReceived sProc;

    @Override
    public void onCreate()
    {
        super.onCreate();

        // Required to support test send feature only
        NBSProxy.register( this );

        // Broadcast receivers for results from NBS message sends
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                boolean success = Activity.RESULT_OK == getResultCode();
                String appID = intent.getStringExtra( "APPID" );
                int datalen = intent.getIntExtra( "DATALEN", 0 );
                if ( action.equals(getString(R.string.msg_sent)) ) {

                } else if ( action.equals(getString(R.string.msg_delivered)) ) {
                    if ( success ) {
                        StatsDB.record( context, true, appID, datalen );
                    }
                }

                // Log.d( TAG, "got intent with action:" + intent.getAction()
                //        + "; success: " + success + "; len: " + len
                //        + "; target: " + appID);
            }
        };

        for ( int id : new int[] {R.string.msg_sent, R.string.msg_delivered}) {
            registerReceiver( br, new IntentFilter( getString(id) ) );
        }
    }

    @Override
    public void onDataReceived(Context context, String fromPhone,
                               byte[] data )
    {
        NBSProxy.OnReceived proc = sProc;
        if ( proc != null ) {
            proc.onDataReceived( context, fromPhone, data );
        }
    }

    public static void setNBSCallback( NBSProxy.OnReceived proc )
    {
        sProc = proc;
    }
}
