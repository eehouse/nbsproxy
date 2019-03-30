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

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import org.eehouse.android.nbsp.R;
import org.eehouse.android.nbsp.BuildConfig;

public class AboutFragment extends PageFragment {
    private static final String TAG = AboutFragment.class.getSimpleName();

    @Override
    public View onCreateView( LayoutInflater inflater,
                              ViewGroup container,
                              Bundle savedInstanceState )
    {
        View view = super.onCreateView( inflater, container, savedInstanceState );

        TextView tv = (TextView)view.findViewById(R.id.version_text);
        DateFormat df = DateFormat.getDateTimeInstance( DateFormat.FULL,
                                                        DateFormat.FULL );
        String dateString = df.format( new Date( BuildConfig.BUILD_STAMP_SECS * 1000 ) );
        tv.setText( getString( R.string.version_text_fmt,
                               BuildConfig.VERSION_NAME,
                               BuildConfig.GIT_REV,
                               dateString ) );

        return view;
    }
}
