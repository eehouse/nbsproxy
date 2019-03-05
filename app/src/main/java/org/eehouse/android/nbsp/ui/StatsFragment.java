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

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import org.eehouse.android.nbsp.R;
import org.eehouse.android.nbsp.StatsDB;
import org.eehouse.android.nbsp.PortReg;

import java.util.List;

public class StatsFragment extends PageFragment {
    private static final String TAG = StatsFragment.class.getSimpleName();

    private TextView mTextView;

    @Override
    void onViewCreated( View view )
    {
        mTextView = (TextView)view.findViewById( R.id.stats_text );
        view.findViewById( R.id.refresh_button )
            .setOnClickListener( new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        refresh();
                    }
                } );
        refresh();
    }

    private void refresh()
    {
        StatsDB.get(getActivity(), new StatsDB.OnHaveWeekRecords() {
                @Override
                public void onHaveData( List<StatsDB.WeekRecord> data )
                {
                    // Let's run this in its own thread. Not sure how long all
                    // these callbacks will take.
                    new RefresherThread( data ).start();
                }
            } );
    }

    private class RefresherThread extends Thread {
        private List<StatsDB.WeekRecord> mData;

        RefresherThread( List<StatsDB.WeekRecord> data ) { mData = data; }

        @Override
        public void run() {
            final Activity activity = getActivity();
            final StringBuilder sb = new StringBuilder();

            for ( final StatsDB.WeekRecord rec : mData ) {
                short port = rec.getPort();
                PortReg.lookup( activity, port,
                                new PortReg.OnHaveAppIDs() {
                                    @Override
                                    public void haveAppIDs( String[] appIDs )
                                    {
                                        String appStr;
                                        for ( int ii = 0; ii < appIDs.length; ++ii ) {
                                            appIDs[ii] = PortReg
                                                .nameFor( getActivity(), appIDs[ii] );
                                        }
                                        if ( appIDs.length == 1 ) { // the common case
                                            sb.append( "app: " ).append( appIDs[0] );
                                        } else {
                                            sb.append( "apps: [" )
                                                .append( TextUtils.join(",",appIDs) )
                                                .append(']');
                                        }
                                        sb.append( ": " ).append( rec ).append('\n');
                                    }
                                } );
            }

            activity.runOnUiThread( new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText( sb.toString() );
                    }
                } );
        } // run
    }
}
