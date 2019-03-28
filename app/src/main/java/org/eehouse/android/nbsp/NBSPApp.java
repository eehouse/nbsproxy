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

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import org.eehouse.android.nbsplib.NBSProxy;

import org.eehouse.android.nbsp.NBSReceiver;
import org.eehouse.android.nbsp.ui.MainActivity;

import org.eehouse.android.libs.apkupgrader.ApkUpgrader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import junit.framework.Assert;

public class NBSPApp extends Application {
    private static final String TAG = NBSPApp.class.getSimpleName();
    private static Map<Integer, NBSProxy.Callbacks> sProcs = new HashMap<>();

    @Override
    public void onCreate()
    {
        Log.d( TAG, "onCreate()" );
        super.onCreate();

        // Required to support test send feature only
        Assert.assertTrue( NBSProxy.isInstalled(this) );
        short port = Short.valueOf( getString( R.string.nbsp_port ) );
        NBSProxy
            .register( this, port, BuildConfig.APPLICATION_ID,
                       new NBSProxy.Callbacks() {
                           @Override
                           public void onProxyAppLaunched()
                           {
                               Log.d( TAG, "onProxyAppLaunched()" );
                           }

                           @Override
                           public void onPermissionsGranted()
                           {
                               Log.d( TAG, "onPermissionsGranted()" );
                           }

                           @Override
                           public void onRegResponse( boolean appReached, boolean needsInitialLaunch )
                           {
                               // There's no way this can fail: I *am* the app....
                               Log.d( TAG, "onRegResponse(appReached=" + appReached + ")");
                               if ( needsInitialLaunch ) {    //  this should be impossible in the app itself
                                   NBSProxy
                                       .postLaunchNotification( NBSPApp.this,
                                                                MainActivity.makeChannelID(NBSPApp.this),
                                                                R.mipmap.ic_launcher_round );
                               }
                           }

                           @Override
                           public void onDataReceived( short port, String fromPhone, byte[] data )
                           {
                               NBSProxy.Callbacks proc = sProcs.remove( Arrays.hashCode(data) );
                               if ( proc != null ) {
                                   try {
                                       proc.onDataReceived( port, fromPhone, data );
                                   } catch ( java.lang.IllegalStateException ise ) {
                                       // This shows when fragment's been detached. Drop it
                                   }
                               }
                           }
                       });

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

        NBSReceiver.onAppLaunched( this );

        ApkUpgrader.getConfig(this)
            .setInterval( ApkUpgrader.Interval.WEEKLY )
            .setScheme( ApkUpgrader.Scheme.HTTPS )
            .setHost( "eehouse.org" )
            .setPath( null )    // script's at http root
            .setAppInfo( BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE,
                         BuildConfig.BUILD_TYPE, BuildConfig.FLAVOR )
            .setForNotifications( MainActivity.makeChannelID( this ),
                                  R.mipmap.ic_launcher_round )
            ;
    }

    public static void setNBSCallback( byte[] data, NBSProxy.Callbacks proc )
    {
        int hash = Arrays.hashCode(data);
        sProcs.put( hash, proc );
    }
}
