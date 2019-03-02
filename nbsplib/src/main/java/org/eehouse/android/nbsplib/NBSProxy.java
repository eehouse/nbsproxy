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

import android.Manifest;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import junit.framework.Assert;

import java.lang.ref.WeakReference;
import java.util.Arrays;

/* Rethinking things: this needs to work in the situation where one of two
 * communicating devices has it installed and the other doesn't.
 *
 * So forget about transmitting appID. Every manifest-registered receiver gets
 * every message anyway, so we'll just let interested apps register by port
 * and broadcast every message to all of them. No runtime lookup of apps: if
 * you don't register, you don't get awakened because I don't know you have a
 * Receiver registered. (I'm not going to try to parse or ping all the
 * installed apps!)
 *
 * So data's unmodified. Gets sent to the other device. If the proxy app's
 * there it'll get it and broadcast. As will any apps doing NBS themselves.
 */

/**
 * This is the public api for the NBSProxy app. Beyond including the library
 * that provides this file, the client just calls NBSProxy.register() from
 * Application.onCreate() and NBSProxy.send() from anywhere.
 */

public class NBSProxy extends BroadcastReceiver {
    private static final String TAG = NBSProxy.class.getSimpleName();
    private static WeakReference<OnReceived> sProcRef;

    // Keys for passing stuff around in intents.
    public static final String EXTRA_PHONE = TAG + ".phone";
    public static final String EXTRA_PORT = TAG + ".port";
    public static final String EXTRA_APPID = TAG + ".appid";
    public static final String EXTRA_DATALEN = TAG + ".len";
    public static final String EXTRA_CMD = TAG + ".cmd";

    // values for EXTRA_CMD
    public static enum CTRL {
        REG,
        SEND,
    }

    public interface OnReceived {
        void onDataReceived( short port, String fromPhone, byte[] data );
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
    public static void register( short port, String appID, OnReceived proc )
    {
        Log.d( TAG, "register(" + proc + ") for appID " + appID );

        // Caller can't just call <code>register( new OnReceived(){} )</code>
        // or with no other reference to the proc it'll get gc'd. So force
        // them to pass an Application that implements OnReceived. If other's
        // use this and care, I'll address.
        assert( proc instanceof Application );
        Context context = (Context)proc; // as long as we're doing the assert :-)

        sProcRef = new WeakReference<>( proc );
        sendRegIntent( context, port, appID );
    }

    /**
     * sends data to app on remote device. Size limit of around 140 bytes.
     *
     * @param phone number of device to send to
     * @param appID appID of app to deliver to on remote device
     * @param data binary data to be transmitted
    */
    public static void send( Context context, String phone,
                             short port, byte[] data )
    {
        Log.d( TAG, "given data of len " + data.length
               + " to send on port " + port );
        String asStr = Base64.encodeToString( data, Base64.NO_WRAP );

        Intent intent = getBaseIntent( CTRL.SEND )
            .putExtra( Intent.EXTRA_TEXT, asStr )
            .putExtra( EXTRA_PHONE, phone )
            .putExtra( EXTRA_APPID, BuildConfig.APPLICATION_ID )
            .putExtra( EXTRA_PORT, port )
            ;
        context.sendBroadcast( intent );
        Log.d( TAG, "launching intent " + intent + " at: org.eehouse.android.nbsp" );
    }

    /**
     * Test where the actual app is installed on the device
     */
    public static boolean isInstalled( Context context )
    {
        boolean installed = true;
        String name = BuildConfig.NBSPROXY_APPLICATION_ID;
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo( name, 0 );
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        Log.d( TAG, "isInstalled() => " + installed );
        return installed;
    }

    public static boolean isGSMPhone( Context context ) // throws something without permission
    {
        boolean result = false;
        TelephonyManager mgr = (TelephonyManager)
            context.getSystemService(Context.TELEPHONY_SERVICE);
        if ( null != mgr ) {
            int type = mgr.getPhoneType();
            result = TelephonyManager.PHONE_TYPE_GSM == type;
        }
        Log.d( TAG, "isGSMPhone() => " + result );
        return result;
    }

    @Override
    public void onReceive( Context context, Intent intent )
    {
        Log.d( TAG, "onReceive()" );
        if ( intent != null
             && Intent.ACTION_SEND.equals(intent.getAction())
             && "text/nbsdata_rx".equals( intent.getType() ) ) {
            String text = intent.getStringExtra( Intent.EXTRA_TEXT );
            String phone = intent.getStringExtra( EXTRA_PHONE );
            short port = intent.getShortExtra( EXTRA_PORT, (short)-1 );
            if ( text == null ) {
                Log.e( TAG, "onReceive(): null text" );
            } else if ( phone == null ) {
                Log.e( TAG, "onReceive(): null phone" );
            } else if ( port == -1 ) {
                Log.e( TAG, "onReceive(): missing port" );
            } else {
                byte[] data = Base64.decode( text, Base64.NO_WRAP );
                if ( sProcRef != null ) {
                    OnReceived proc = sProcRef.get();
                    if ( proc != null ) {
                        Log.d( TAG, "onReceive(): passing " + data.length + " bytes from "
                               + phone );
                        proc.onDataReceived( port, phone, data );
                    }
                }
            }
        }
    }

    private static void sendRegIntent( Context context, short port, String appID )
    {
        Intent intent = getBaseIntent( CTRL.REG )
            .putExtra( EXTRA_PORT, port )
            .putExtra( EXTRA_APPID, appID )
            ;
        Log.d( TAG, "sendRegIntent() sending " + intent );
        context.sendBroadcast( intent );
    }

    private static Intent getBaseIntent( CTRL cmd )
    {
        Intent intent = new Intent()
            .putExtra( EXTRA_CMD, cmd.ordinal() )
            .setAction( Intent.ACTION_SEND )
            .setType( "text/nbsdata_tx" )
            .setPackage( BuildConfig.NBSPROXY_APPLICATION_ID )
            ;
        return intent;
    }
}
