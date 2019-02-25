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

package org.eehouse.android.nbsp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.support.design.widget.TabLayout;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST = 4351;
    private static final String[] PERMISSIONS_REQUIRED = {
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
    };
    // Keep these two arrays in sync
    private static final int sTabTitles[] = { R.string.tab_about_title,
                                              R.string.tab_perms_title,
                                              R.string.tab_test_title,
                                              R.string.tab_stats_title,
    };
    static final int[] sLayouts = {R.layout.fragment_about,
                                   R.layout.fragment_perms,
                                   R.layout.fragment_test,
                                   R.layout.fragment_stats,
    };

    private CheckBox mPermsCheck;

    // This will be required eventually to request permissions.
    @Override
    protected void onCreate( Bundle sis )
    {
        super.onCreate(sis);
        setContentView( R.layout.main_pager );

        ViewPager viewPager = (ViewPager)findViewById( R.id.viewpager );
        viewPager.setAdapter(new SampleFragmentPagerAdapter(getSupportFragmentManager(), 
            this));

        // Give the TabLayout the ViewPager
        TabLayout tabLayout = (TabLayout)findViewById( R.id.sliding_tabs );
        tabLayout.setupWithViewPager( viewPager );

        // mPermsCheck = (CheckBox)findViewById( R.id.perms_checkbox);
        // recheckCheck();
        // mPermsCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        //         @Override
        //         public void onCheckedChanged(CompoundButton cb,
        //                                      boolean checked) {
        //             Log.d( TAG, "got: " + checked );
        //             if (checked) {
        //                 requestPermissions();
        //             } else {
        //                 disablePermissions();
        //             }
        //         }
        //     } );

        // findViewById(R.id.uninstall_button)
        //     .setOnClickListener(new View.OnClickListener() {
        //             @Override
        //             public void onClick(View view) {
        //                 Intent intent = new Intent(Intent.ACTION_DELETE)
        //                     .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID ) )
        //                     .putExtra( "android.intent.extra.UNINSTALL_ALL_USERS", true);
        //                 startActivity(intent);
        //             }
        //         } );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults )
    {
        if (requestCode == PERMISSIONS_REQUEST) {
            recheckCheck();
        }
    }

    private void recheckCheck()
    {
        mPermsCheck.setChecked( havePermissions() );
    }

    private boolean havePermissions()
    {
        boolean granted = true;
        for ( String perm : PERMISSIONS_REQUIRED ) {
            granted = granted &&
                (ContextCompat.checkSelfPermission(this, perm ) == PackageManager.PERMISSION_GRANTED);
        }
        return granted;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST);
    }

    private void disablePermissions() {
    }

    public class SampleFragmentPagerAdapter extends FragmentPagerAdapter {
        // final int PAGE_COUNT = 3;
        private Context mContext;

        public SampleFragmentPagerAdapter(FragmentManager fm, Context context) {
            super(fm);
            mContext = context;
        }

        @Override
        public int getCount() {
            return sTabTitles.length;
        }

        @Override
        public Fragment getItem( int position )
        {
            return PageFragment.newInstance( position );
        }

        @Override
        public CharSequence getPageTitle( int position )
        {
            // Generate title based on item position
            return mContext.getString( sTabTitles[position] );
        }
    }
}
