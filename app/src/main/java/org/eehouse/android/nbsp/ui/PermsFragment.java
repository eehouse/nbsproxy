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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;

import org.eehouse.android.nbsp.BuildConfig;
import org.eehouse.android.nbsp.NBSReceiver;
import org.eehouse.android.nbsp.R;

public class PermsFragment extends PageFragment {
    private static final String TAG = PermsFragment.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST = 4351;
    private static final String[] PERMISSIONS_REQUIRED = {
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE,
    };
    private View mView;

    @Override
    void onViewCreated( View view )
    {
        mView = view;
        // mPermsButton = (Button)view.findViewById( R.id.perms_button );
        recheckCheck();
        view.findViewById( R.id.perms_button )
            .setOnClickListener( new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        requestPermissions( PERMISSIONS_REQUIRED,
                                            PERMISSIONS_REQUEST );
                    }
                } );

        view.findViewById( R.id.settings_button )
            .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Uri uri = Uri.fromParts("package",
                                BuildConfig.APPLICATION_ID, null);
                        Intent intent = new Intent()
                            .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(uri);
                        startActivity(intent);
                    }
                } );
        view.findViewById( R.id.exit_button )
            .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        getActivity().finish();
                    }
                } );
    }

    private void recheckCheck()
    {
        boolean needPerms = !havePermissions(getActivity());
        mView.findViewById(R.id.need_perms)
            .setVisibility( needPerms ? View.VISIBLE : View.GONE );
        mView.findViewById(R.id.have_perms)
            .setVisibility( needPerms ? View.GONE : View.VISIBLE );
    }

    static boolean havePermissions( Activity activity )
    {
        boolean granted = true;
        for ( String perm : PERMISSIONS_REQUIRED ) {
            granted = granted &&
                (ContextCompat.checkSelfPermission( activity, perm )
                 == PackageManager.PERMISSION_GRANTED);
        }
        return granted;
    }

    @Override
    public void onRequestPermissionsResult( int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults)
    {
        if (requestCode == PERMISSIONS_REQUEST) {
            recheckCheck();

            Activity activity = getActivity();
            if ( activity != null && havePermissions( activity ) ) {
                NBSReceiver.onPermissionsGained( activity );
            }
        }
    }
}
