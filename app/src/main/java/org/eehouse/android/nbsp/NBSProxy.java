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

package org.eehouse.android.nbsp;

import android.util.Base64;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.util.Arrays;

/*
 * This is the public api for the NBSProxy app. It (i.e. this file) is meant
 * to be included in a client app's source tree, after which the client just
 * calls NBSProxy.send().
 *
 * Note that the client must also provide an entrypoint for incoming messages
 * from NBSProxy something like this:

    <activity android:name="NBSReceive">
      <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/nbsdata" />
      </intent-filter>
    </activity>

 * in which the receiver of the Intent calls NBSProxy.getFrom( intent ) to
 * retrieve the sender's phone number and data.
 */

public class NBSProxy {
    private static final String TAG = NBSProxy.class.getSimpleName();

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
        Log.d( TAG, "got data of len " + data.length
               + " with hash " + Arrays.hashCode(data) );
        String asStr = Base64.encodeToString( data, Base64.NO_WRAP );

        Intent intent = new Intent()
            .setAction( Intent.ACTION_SEND )
            .putExtra( Intent.EXTRA_TEXT, asStr )
            .putExtra( "PHONE", phone )
            .putExtra( "APPID", appID )
            .putExtra( "HASH", Arrays.hashCode(data) )
            .setPackage( "org.eehouse.android.nbsp" )
            .setType( "text/nbsdata" );
        context.sendBroadcast( intent );
        Log.d( TAG, "launching intent at: org.eehouse.android.nbsp" );
    }

    public static class Incoming {
        public String phone;
        public byte[] data;
        Incoming( String pPhone, byte[] pData ) {
            phone = pPhone;
            data = pData;
        }
    }

    /**
     * Pulls data out of intent and decodes into Incoming pojo whence sender
     * phone number and data can be retrieved.
     *
     * @param intent Intent passed into client's BroadcastReceiver or app by
     * that likely came from NBSProxy instance on device.
     * @return Incoming pojo, or null if not successful
     */
    public static Incoming getFrom( Intent intent ) {
        Incoming result = null;
        if ( Intent.ACTION_SEND.equals(intent.getAction())
             && "text/nbsdata".equals( intent.getType() ) ) {
            String text = intent.getStringExtra( Intent.EXTRA_TEXT );
            String phone = intent.getStringExtra( "PHONE" );
            if ( text != null && phone != null ) {
                byte[] data = Base64.decode( text, Base64.NO_WRAP );
                result = new Incoming( phone, data );
            }
        }
        return result;
    }
}
