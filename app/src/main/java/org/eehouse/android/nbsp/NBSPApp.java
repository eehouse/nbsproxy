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

import org.eehouse.android.nbsplib.NBSProxy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import junit.framework.Assert;

public class NBSPApp extends Application implements NBSProxy.OnReceived {
    private static final String TAG = NBSPApp.class.getSimpleName();
    private static Map<Integer, NBSProxy.OnReceived> sProcs = new HashMap<>();;

    @Override
    public void onCreate()
    {
        super.onCreate();

        NBSProxy.isGSMPhone( this );

        // Required to support test send feature only
        Assert.assertTrue( NBSProxy.isInstalled(this) );
        short port = Short.valueOf( getString( R.string.nbsp_port ) );
        NBSProxy.register( port, BuildConfig.APPLICATION_ID, this );

        // Broadcast receivers for results from NBS message sends. Right now
        // we just log what happened.
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                boolean success = Activity.RESULT_OK == getResultCode();
                Log.d( TAG, "notified of nbs send progress: action: " + action
                       + "; success: " + success );
            }
        };

        for ( int id : new int[] {R.string.msg_sent, R.string.msg_delivered}) {
            registerReceiver( br, new IntentFilter( getString(id) ) );
        }
    }

    @Override
    public void onDataReceived( short port, String fromPhone, byte[] data )
    {
        NBSProxy.OnReceived proc = sProcs.remove( Arrays.hashCode(data) );
        if ( proc != null ) {
            try {
                proc.onDataReceived( port, fromPhone, data );
            } catch ( java.lang.IllegalStateException ise ) {
                // This shows when fragment's been detached. Drop it
            }
        }
    }

    public static void setNBSCallback( byte[] data, NBSProxy.OnReceived proc )
    {
        int hash = Arrays.hashCode(data);
        sProcs.put( hash, proc );
    }
}
