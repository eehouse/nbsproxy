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
import org.eehouse.android.nbsp.NBSApp;
import org.eehouse.android.nbsp.NBSProxy;
import org.eehouse.android.nbsp.R;

public class TestFragment extends PageFragment {
    private static final String TAG = TestFragment.class.getSimpleName();
    @Override
    void onViewCreated( View view )
    {
        Button button = (Button)view.findViewById( R.id.test_button );
        button.findViewById( R.id.test_button )
            .setOnClickListener( new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        runTest();
                    }
                } );

        String buttonLabel = getString( R.string.test_button_label_fmt, getPhoneNumber());
        button.setText( buttonLabel );
    }

    private void runTest()
    {
        final long startTime = System.currentTimeMillis();
        String phone = getPhoneNumber();
        Random random = new Random();
        final byte[] data = new byte[24 + random.nextInt(24)]; // 24-48 bytes
        random.nextBytes( data );

        NBSApp.setNBSCallback(new NBSProxy.OnReceived() {
                @Override
                public void onDataReceived( Context context,
                                            String fromPhone,
                                            byte[] dataIn )
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

        NBSProxy.send( getActivity(), phone, BuildConfig.APPLICATION_ID, data );
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
