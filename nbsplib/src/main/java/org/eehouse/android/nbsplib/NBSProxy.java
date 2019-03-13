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

package org.eehouse.android.nbsplib;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import junit.framework.Assert;

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
    private static Callbacks sProcs;
    private static Object sWaiter = new Object();

    private static String sClientAppID;
    private static final String TAG() { return TAG + "_" + sClientAppID; }

    // Keys for passing stuff around in intents.
    public static final String EXTRA_VERSION = TAG + ".version";
    public static final String EXTRA_PHONE = TAG + ".phone";
    public static final String EXTRA_PORT = TAG + ".port";
    public static final String EXTRA_APPID = TAG + ".appid";
    public static final String EXTRA_DATALEN = TAG + ".len";
    public static final String EXTRA_CMD = TAG + ".cmd";
    public static final String EXTRA_REGTIME = TAG + ".regTime";
    public static final String EXTRA_REGRESPTIME = TAG + ".respTime";
    public static final String EXTRA_ERROR_MSG = TAG + ".errmsg";
    public static final String EXTRA_CLIENTOLD = TAG + ".clientOld";

    public static final String ACTION_CTRL = "org.eehouse.android.nbsplib.action_ctrl";

    // How long we wait for reg response before suspecting that the NBSProxy
    // app needs the user to launch it manually for the critical first time in
    // order that it start being delivered Intents for which its Manifest
    // registers it.
    private static final long REG_WAIT_MILLIS = 1000 * 20;

    private static Thread sWaitThread;
    private static RegInfo sRegInfo;
    private static boolean sIsRegistered;

    // values for EXTRA_CMD
    public enum CTRL {
        REG,
        SEND,
        APP_LAUNCHED,
        PERMS_GRANTED,
    }

    public interface Callbacks {
        void onProxyAppLaunched(); // might not need this
        void onPermissionsGranted();
        void onRegResponse( boolean appReached, boolean needsInitialLaunch );
        void onDataReceived( short port, String fromPhone, byte[] data );
    }

    /**
     * Meant to be called by your Application's onCreate (which should be
     * called as part of bringing you up for a BroadcastReceiver to be
     * called), this allows you to register the callback that will receive the
     * data in each incoming message.
     *
     * @param port NBS port on which to receive data
     *
     * @param appID application id (e.g. org.eehouse.android.nbsp) of your
     * app, and to which data will be delivered.
     *
     * @param procs reference to your callback that <em>MUST</em> be an
     * Application instance that implements NBSProxy.Callbacks. That's to
     * force you to pass something that won't be gc'd prematurely, which in
     * turn lets me store it in a WeakReference that won't cause any leaks.
     * That way you don't need to clear it later: gc's magic. (Note: I'll
     * revist this, as I suspect it's wrong. But it works for now even if it's
     * too cautious.)
     *
     * @return false if unable to contact the app because an earlier
     * registration is still waiting a response.
     */
    public static boolean register( Context context, short port,
                                    String appID, Callbacks procs )
    {
        sClientAppID = appID;
        sProcs = procs;
        if ( sRegInfo != null ) {
            Log.e(TAG(), "register(): reg already pending; dropping it" );
        }
        sRegInfo = new RegInfo( port, appID );
        return tryRegister( context );
    }

    private static boolean tryRegister( Context context )
    {
        Log.d( TAG(), "tryRegister()" );
        boolean result = false;
        if ( !isInstalledImpl( context ) ) {
            Log.e( TAG(), "tryRegister(): NBSProxy not installed; later..." );
        } else if ( !sIsRegistered && sRegInfo != null ) {
            // Caller can't just call <code>register( new Callbacks(){} )</code>
            // or with no other reference to the proc it'll get gc'd. So force
            // them to pass an Application that implements Callbacks. If other's
            // use this and care, I'll address.

            boolean threadNotRunning = false;
            synchronized ( NBSProxy.class ) {
                threadNotRunning = sWaitThread == null;
                if ( threadNotRunning ) {
                    sWaitThread = startWaitThread( context );
                }
            }

            if ( threadNotRunning ) {
                sendRegIntent( context );

                startReceiver( context );
            }
            result = threadNotRunning;
        }
        return result;
    }

    /**
     * Utility function to post, using app's channel etc., a notification with
     * explanation that if selected will launch NBSProxy, after which it
     * should respond to Intents and be able to ask for the permissions it needs
     *
     * @param channelID The client app's notification channel, already established
     *
     * @param iconID The client app's small icon for notifications
     *
     */
    public static void postLaunchNotification( Context context, String channelID,
                                               int iconID )
    {
        Intent intent = context.getPackageManager()
            .getLaunchIntentForPackage( BuildConfig.NBSPROXY_APPLICATION_ID );
        if ( intent != null ) { // installed?
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP );

            PendingIntent pi = PendingIntent
                .getActivity( context, 1000, intent, PendingIntent.FLAG_ONE_SHOT );

            Notification notification =
                new NotificationCompat.Builder( context, channelID )
                .setContentIntent( pi )
                .setSmallIcon( iconID )
                .setContentTitle( context.getString( R.string.launch_notify_title ) )
                .setContentText( context.getString( R.string.launch_notify_body ) )
                .setAutoCancel( true )
                .build();

            NotificationManager nm = (NotificationManager)
                context.getSystemService( Context.NOTIFICATION_SERVICE );
            nm.notify( iconID, notification );
        }
    }

    /**
     * sends data to app on remote device. Size limit of around 140 bytes.
     *
     * @param phone number of device to send to
     * @param port NBS port at which to deliver on remote device
     * @param data binary data to be transmitted
    */
    public static void send( Context context, String phone,
                             short port, byte[] data )
    {
        Log.d( TAG(), "given data of len " + data.length
               + " to send on port " + port );
        String asStr = Base64.encodeToString( data, Base64.NO_WRAP );

        Intent intent = getBaseIntent( CTRL.SEND )
            .putExtra( Intent.EXTRA_TEXT, asStr )
            .putExtra( EXTRA_PHONE, phone )
            .putExtra( EXTRA_APPID, context.getPackageName() )
            .putExtra( EXTRA_PORT, port )
            ;
        context.sendBroadcast( intent );
        Log.d( TAG(), "launching intent " + intent + " at: org.eehouse.android.nbsp" );
    }

    /**
     * Test where the actual app is installed on the device
     */
    private static boolean sWasInstalled = false;

    private static long getInstallTime( Context context )
    {
        long result = 0;
        String name = BuildConfig.NBSPROXY_APPLICATION_ID;
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo( name, 0 );
            result = pi.firstInstallTime;
        } catch (PackageManager.NameNotFoundException | NullPointerException e) {
        }

        Log.d( TAG(), "getInstallTime() => " + result + " ("
               + (System.currentTimeMillis() - result) / 1000 + " seconds ago)" );
        return result;
    }

    /**
     *
     * @param context You know what this is.
     *
     * @return whether the PackageManager thinks the NBSProxy app is
     * installed.
     */
    public static boolean isInstalled( Context context )
    {
        boolean installed = isInstalledImpl( context );
        if ( !installed ) {
            sIsRegistered = false;
        }

        if ( installed && !sWasInstalled ) {
            Log.d( TAG(), "isInstalled(): first time!" );
            tryRegister( context );
        }
        sWasInstalled = installed;

        Log.d( TAG(), "isInstalled() => " + installed );
        return installed;
    }

    private static boolean isInstalledImpl( Context context )
    {
        boolean installed = getInstallTime( context ) > 0;
        Log.d( TAG(), "isInstalledImpl() => " + installed );
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
        Log.d( TAG(), "isGSMPhone() => " + result );
        return result;
    }

    @Override
    public void onReceive( Context context, Intent intent )
    {
        Log.d( TAG(), "onReceive()" );
        if ( intent != null
             && Intent.ACTION_SEND.equals(intent.getAction())
             && "text/nbsdata_rx".equals( intent.getType() ) ) {

            persistReceiveTime( context );

            if ( 0 == versionOk( intent ) ) {
                if ( handleRegResponse( context, intent ) ) {
                    // nothing to do
                } else {
                    String text = intent.getStringExtra( Intent.EXTRA_TEXT );
                    String phone = intent.getStringExtra( EXTRA_PHONE );
                    short port = intent.getShortExtra( EXTRA_PORT, (short)-1 );
                    if ( text == null ) {
                        Log.e( TAG(), "onReceive(): null text" );
                    } else if ( phone == null ) {
                        Log.e( TAG(), "onReceive(): null phone" );
                    } else if ( port == -1 ) {
                        Log.e( TAG(), "onReceive(): missing port" );
                    } else {
                        byte[] data = Base64.decode( text, Base64.NO_WRAP );
                        Callbacks procs = sProcs;
                        if ( procs != null ) {
                            Log.d( TAG(), "onReceive(): passing " + data.length + " bytes from "
                                   + phone );
                            procs.onDataReceived( port, phone, data );
                        }
                    }
                }
            }
        }
    }

    /**
     * Called by the NBSProxy app. Client apps don't need this.
     *
     * Like strcmp, returns 0 if they're the same, a negative number if the
     * version in the intent is older and a postive one if the local version
     * is older.
     *
     * Version string is something like 1.2.3, with 1 and 2 the major and
     * minor version numbers and 3 the release. If the first two are the same
     * the versions are compatible and match.
     *
     * @param intent Intent received from the other side
     *
     * @return Like strcmp, returns 0 if they're the same, a negative number
     * if the version in the intent is older and a postive one if the local
     * version is older.
     */
    public static int versionOk( Intent intent )
    {
        int result = 0;
        String version = intent.getStringExtra( EXTRA_VERSION );
        if ( version == null || version.length() == 0 ) {
            result = -1;        // missing means you're older
        } else {
            String[] received = TextUtils.split(version, "\\.");
            String[] mine = TextUtils.split(BuildConfig.NBSP_VERSION, "\\.");
            Assert.assertTrue( mine.length == 3 );
            if ( received.length != 3 ) {
                result = -1;    // bad format? You're older
            } else {
                for ( int ii = 0; result == 0 && ii < 2; ++ii ) {
                    result = Integer.valueOf(mine[ii]) - Integer.valueOf(received[ii]);
                }
            }
        }
        // Log.d( TAG(), "versionsOk(other: " + version + ", me: "
        //        + BuildConfig.NBSP_VERSION + ") => " + result);
        return result;
    }

    private static void sendRegIntent( Context context )
    {
        if ( sRegInfo != null ) {
            Intent intent = getBaseIntent( CTRL.REG )
                .putExtra( EXTRA_PORT, sRegInfo.port )
                .putExtra( EXTRA_APPID, sRegInfo.appID )
                .putExtra( EXTRA_REGTIME, System.currentTimeMillis() )
                ;
            Log.d( TAG(), "sendRegIntent() sending " + intent );
            context.sendBroadcast( intent );
        }
    }

    // Return true IFF it's a reg response and not a message to be forwarded.
    private boolean handleRegResponse( Context context, Intent intent )
    {
        boolean isRegResponse = false;
        long regTime = intent.getLongExtra( NBSProxy.EXTRA_REGTIME, -1 );
        if ( regTime != -1 ) {
            long respTime = intent.getLongExtra( NBSProxy.EXTRA_REGRESPTIME, -1 );
            if ( respTime != -1 ) {
                isRegResponse = true;
                sIsRegistered = true;

                long waitTime = System.currentTimeMillis() - regTime;
                Log.d( TAG(), "got regResponse; round trip took " + waitTime + "ms" );
                synchronized ( sWaiter ) {
                    sWaiter.notify();
                }
            }
        }
        return isRegResponse;
    }

    private static BroadcastReceiver sReceiver;
    private synchronized static void startReceiver( Context context )
    {
        if ( sReceiver == null ) {
            sReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive( Context context, Intent intent ) {
                    Log.d( TAG(), "onReceive(" + intent + ")");
                    CTRL cmd = CTRL.values()[intent.getIntExtra( EXTRA_CMD, -1 )];
                    Callbacks procs = sProcs;
                    Log.d( TAG(), "got cmd: " + cmd );
                    switch( cmd ) {
                    case PERMS_GRANTED:
                        if ( procs != null ) {
                            procs.onPermissionsGranted();
                        }
                        break;
                    case APP_LAUNCHED:
                        if ( procs != null ) {
                            tryRegister( context );
                            procs.onProxyAppLaunched();
                        }
                        break;
                    }
                }
            };
            IntentFilter ifltr = new IntentFilter( ACTION_CTRL );
            context.registerReceiver( sReceiver, ifltr );
        }
    }

    private static Thread startWaitThread( final Context context )
    {
        Log.d( TAG(), "startWaitThread()" );
        Thread result = new Thread( new Runnable() {
                @Override
                public void run() {
                    Log.d( TAG(), "startWaitThread.run()" );
                    synchronized ( sWaiter ) {
                        try {
                            long startMS = System.currentTimeMillis();
                            sWaiter.wait( REG_WAIT_MILLIS );
                            long tookMillis = System.currentTimeMillis() - startMS;
                            Log.d( TAG(), "startWaitThread(appID=" + sRegInfo.appID
                                   + "): wait returned after " + tookMillis + "ms" );
                            Callbacks procs = sProcs;
                            if ( procs != null ) {
                                boolean appReached = tookMillis < REG_WAIT_MILLIS;
                                boolean needsInitialLaunch =
                                    getInstallTime( context ) > getReceiveTime( context );
                                // the app can't need launching if we reached it!
                                Assert.assertTrue( !needsInitialLaunch || !appReached );
                                Log.d( TAG(), "calling onRegResponse(appReached=" + appReached
                                       + ", needsInitialLaunch=" + needsInitialLaunch + ")");
                                procs.onRegResponse( appReached, needsInitialLaunch );
                            } else {
                                Log.e( TAG(), "startWaitThread(): no callbacks!!" );
                            }
                        } catch ( InterruptedException ex ) {
                            Log.d( TAG(), "startWaitThread(): interrupted: " +
                                   ex.getMessage() );
                        }
                    }
                    synchronized ( NBSProxy.class ) {
                        sWaitThread = null;
                    }
                    Log.d( TAG(), "startWaitThread.run() DONE" );
                }
            } );
        result.start();
        return result;
    }

    private static Intent getBaseIntent( CTRL cmd )
    {
        Intent intent = new Intent()
            .putExtra( EXTRA_VERSION, BuildConfig.NBSP_VERSION )
            .putExtra( EXTRA_CMD, cmd.ordinal() )
            .setAction( Intent.ACTION_SEND )
            .setType( "text/nbsdata_tx" )
            .setPackage( BuildConfig.NBSPROXY_APPLICATION_ID )
            ;
        return intent;
    }

    // We want to remember when we've received data, which means the NBSProxy
    // application is able to communicate. Until that time we might need to
    // prompt the user to launch it so it can start receiving intents.
    private static final String HIDDEN_PREFS = TAG + ".nbsp_hidden";
    private static final String KEY_RECEIVE_STAMP = TAG + ".receiveStamp";
    private static void persistReceiveTime( Context context )
    {
        long stamp = System.currentTimeMillis();
        context.getSharedPreferences( HIDDEN_PREFS, Context.MODE_PRIVATE )
            .edit()
            .putLong( KEY_RECEIVE_STAMP, stamp )
            .apply()
            ;
    }

    private static long getReceiveTime( Context context )
    {
        long result = context
            .getSharedPreferences( HIDDEN_PREFS, Context.MODE_PRIVATE )
            .getLong( KEY_RECEIVE_STAMP, 0);
        Log.d( TAG(), "getReceiveTime() => " + result );
        return result;
    }

    private static class RegInfo {
        short port;
        String appID;
        public RegInfo( short port, String appID ) {
            this.port = port;
            this.appID = appID;
        }
    }
}
