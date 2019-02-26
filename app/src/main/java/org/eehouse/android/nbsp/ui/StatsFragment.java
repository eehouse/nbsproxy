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

import android.view.View;
import android.widget.TextView;

import org.eehouse.android.nbsp.R;
import org.eehouse.android.nbsp.StatsDB;

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
        StatsDB.getRecords(getActivity(), new StatsDB.OnHaveData() {
                @Override
                public void onHaveData( final List<StatsDB.WeekRecord> data )
                {
                    getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                StringBuilder sb = new StringBuilder();
                                for ( StatsDB.WeekRecord rec : data ) {
                                    sb.append( rec ).append('\n');
                                }
                                mTextView.setText( sb.toString() );
                            }
                        } );
                }
            } );
    }
}
