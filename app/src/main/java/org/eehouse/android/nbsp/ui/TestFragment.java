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

package org.eehouse.android.nbsp.ui;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.Arrays;
import java.util.Random;

import org.eehouse.android.nbsp.BuildConfig;
import org.eehouse.android.nbsp.NBSPApp;
import org.eehouse.android.nbsp.R;
import org.eehouse.android.nbsplib.NBSProxy;

public class TestFragment extends PageFragment {
    private static final String TAG = TestFragment.class.getSimpleName();

    private Button mTestButton;

    @Override
    void onViewCreated( View view )
    {
        mTestButton = (Button)view.findViewById( R.id.test_button );
        mTestButton.findViewById( R.id.test_button )
            .setOnClickListener( new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        runTest();
                    }
                } );
        setButtonText();
    }

    // This works to update the phone number after permission's granted
    // because it's called as a result of the OS's permissions alert losing
    // focus in our favor, causing the whole activity to resume. It is *not*
    // called when a fragment's simply paged into view.
    @Override
    public void onResume() {
        super.onResume();
        setButtonText();
    }

    private void setButtonText()
    {
        String buttonLabel = getString( R.string.test_button_label_fmt, getPhoneNumber());
        mTestButton.setText( buttonLabel );
    }

    private void runTest()
    {
        final long startTime = System.currentTimeMillis();
        String phone = getPhoneNumber();
        Random random = new Random();
        final byte[] data = new byte[24 + random.nextInt(24)]; // 24-48 bytes
        random.nextBytes( data );

        NBSPApp.setNBSCallback(data, new NBSProxy.Callbacks() {

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
                }

                @Override
                public void onDataReceived( short port, String fromPhone, byte[] dataIn )
                {
                    if ( Arrays.equals( data, dataIn ) ) {
                        long elapsedMS = System.currentTimeMillis() - startTime;
                        String msg = getString( R.string.test_result_fmt,
                                                ((float)elapsedMS)/1000 );

                        new AlertDialog.Builder(getActivity())
                            .setMessage(msg)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    }
                }
            } );

        short port = Short.valueOf( getString( R.string.nbsp_port ) );
        NBSProxy.send( getActivity(), phone, port, data );
    }

    private String getPhoneNumber()
    {
        String phoneNumber = "???";
        if ( PermsFragment.havePermissions( getActivity() ) ) {
            TelephonyManager tMgr = (TelephonyManager)getActivity()
                .getSystemService( Context.TELEPHONY_SERVICE );
            phoneNumber = tMgr.getLine1Number();
        }
        return phoneNumber;
    }
}
