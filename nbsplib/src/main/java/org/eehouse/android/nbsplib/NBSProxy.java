/* -*- compile-command: "find-and-gradle.sh inXw4Deb"; -*- */
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

package org.eehouse.android.nbsplib;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import junit.framework.Assert;

import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * This is the public api for the NBSProxy app. It (i.e. this file) is meant
 * to be included in a client app's source tree, after which the client just
 * calls NBSProxy.register() from Application.onCreate() and NBSProxy.send()
 * from anywhere
 *
 * Note that the client must also provide an entrypoint for incoming messages
 * from NBSProxy thus (literally):
 *
 *  <receiver android:name="org.eehouse.android.nbsp.NBSProxy">
 *    <intent-filter>
 *      <action android:name="android.intent.action.SEND" />
 *      <category android:name="android.intent.category.DEFAULT" />
 *      <data android:mimeType="text/nbsdata_rx" />
 *    </intent-filter>
 *  </receiver>
 *
 * With the receiver in place, incoming NBS messages are delivered to the
 * callback passed to register().
 */

public class NBSProxy extends BroadcastReceiver {
    private static final String TAG = NBSProxy.class.getSimpleName();
    private static WeakReference<OnReceived> sProcRef;

    public interface OnReceived {
        void onDataReceived( Context context, String fromPhone, byte[] data );
    }

    /**
     * Meant to be called by your Application's onCreate (which should be
     * called as part of bringing you up for a BroadcastReceiver to be
     * called), this allows you to register the callback that will receive the
     * data in each incoming message.
     *
     * @param proc reference to your callback that <em>MUST</em> be an
     * Application instance that implements NBSProxy.OnReceived. That's to
     * force you to pass something that won't be gc'd prematurely, which in
     * turn lets me store it in a WeakReference that won't cause any leaks.
     * You don't need to clear it later by passing null: gc's magic.
     */
    public static void register( OnReceived proc )
    {
        Log.d( TAG, "register(" + proc + ")" );

        // Caller can't just call <code>register( new OnReceived(){} )</code>
        // or with no other reference to the proc it'll get gc'd. So force
        // them to pass an Application that implements OnReceived. If other's
        // use this and care, I'll address.
        assert( proc instanceof Application );

        sProcRef = new WeakReference<>( proc );
    }

    /**
     * sends data to app on remote device. Size limit of around 140 bytes.
     *
     * @param phone number of device to send to
     * @param appID appID of app to deliver to on remote device
     * @param data binary data to be transmitted
    */
    public static void send( Context context, String phone,
                             String appID, byte[] data )
    {
        Log.d( TAG, "given data of len " + data.length
               + " with hash " + Arrays.hashCode(data)
               + " for appid " + appID );
        String asStr = Base64.encodeToString( data, Base64.NO_WRAP );

        Intent intent = new Intent()
            .setAction( Intent.ACTION_SEND )
            .putExtra( Intent.EXTRA_TEXT, asStr )
            .putExtra( "PHONE", phone )
            .putExtra( "APPID", appID )
            .putExtra( "HASH", Arrays.hashCode(data) )
            .setPackage( "org.eehouse.android.nbsp" )
            .setType( "text/nbsdata_tx" );
        context.sendBroadcast( intent );
        Log.d( TAG, "launching intent at: org.eehouse.android.nbsp" );
    }

    /**
     * Test where the actual app is installed on the device
     */
    public static boolean isInstalled( Context context )
    {
        Assert.fail();          // Need to figure out how to get the app's package name
        boolean installed = true;
        String name = NBSProxy.class.getPackage().getName();
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo( name, 0 );
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        Log.d( TAG, "isInstalled() => " + installed );
        return installed;
    }

    @Override
    public void onReceive( Context context, Intent intent )
    {
        Log.d( TAG, "onReceive()" );
        if ( intent != null
             && Intent.ACTION_SEND.equals(intent.getAction())
             && "text/nbsdata_rx".equals( intent.getType() ) ) {
            String text = intent.getStringExtra( Intent.EXTRA_TEXT );
            String phone = intent.getStringExtra( "PHONE" );
            if ( text != null && phone != null ) {
                byte[] data = Base64.decode( text, Base64.NO_WRAP );
                boolean sent = false;
                if ( sProcRef != null ) {
                    OnReceived proc = sProcRef.get();
                    if ( proc != null ) {
                        Log.d( TAG, "passing " + data.length + " bytes from "
                               + phone );
                        proc.onDataReceived( context, phone, data );
                        sent = true;
                    }
                }
                if ( !sent ) {
                    Log.e( TAG, "no callback found; message dropped" );
                }
            }
        }
    }
}
