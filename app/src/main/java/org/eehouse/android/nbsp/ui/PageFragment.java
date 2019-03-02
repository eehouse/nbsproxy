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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.eehouse.android.nbsp.BuildConfig;
import org.eehouse.android.nbsp.R;

public class PageFragment extends Fragment {
    private static final String ARG_PAGE_TITLE = "ARG_PAGE_TITLE";
    private static final String ARG_PAGE_POS = "ARG_PAGE_POS";

    private int mPageIndex;

    public static PageFragment newInstance( int pos )
    {
        PageFragment result = null;
        Bundle args = new Bundle();
        args.putInt( ARG_PAGE_POS, pos );
        switch ( MainActivity.sLayouts[pos] ) {
        case R.layout.fragment_about:
            result = new AboutFragment();
            break;
        case R.layout.fragment_perms:
            result = new PermsFragment();
            break;
        case R.layout.fragment_test:
            result = new TestFragment();
            break;
        case R.layout.fragment_stats:
            result = new StatsFragment();
            break;
        }
        result.setArguments( args );
        return result;
    }

    @Override
    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        mPageIndex = getArguments().getInt( ARG_PAGE_POS );
    }

    @Override
    public View onCreateView( LayoutInflater inflater,
                              ViewGroup container,
                              Bundle savedInstanceState )
    {
        View view = inflater.inflate(MainActivity.sLayouts[mPageIndex],
                                     container, false);
        onViewCreated( view );

        // Two subclasses support Uninstall button, so handle here
        View button = view.findViewById(R.id.uninstall_button);
        if ( button != null ) {
            button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(Intent.ACTION_DELETE)
                            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID ) )
                            .putExtra( "android.intent.extra.UNINSTALL_ALL_USERS", true);
                        startActivity(intent);
                    }
                } );
        }

        return view;
    }

    // Subclasses that do more than display need to override this to set up
    // handlers etc.
    void onViewCreated( View view ) {}
}
