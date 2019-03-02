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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import org.eehouse.android.nbsp.PortReg;
import org.eehouse.android.nbsp.R;
import org.eehouse.android.nbsp.ui.PageFragment;
import org.eehouse.android.nbsplib.NBSProxy;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int ID_OFFSET = 1000;

    private static MainActivity sSelf;

    // Keep these two arrays in sync
    private static final int sTabTitles[] = { R.string.tab_about_title,
                                              R.string.tab_perms_title,
                                              R.string.tab_test_title,
                                              R.string.tab_stats_title,
    };
    public static final int[] sLayouts = {R.layout.fragment_about,
                                          R.layout.fragment_perms,
                                          R.layout.fragment_test,
                                          R.layout.fragment_stats,
    };


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

        showAlertIf( getIntent() );
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sSelf = this;
    }

    @Override
    protected void onStop() {
        super.onStop();
        sSelf = null;
    }

    @Override
    protected void onNewIntent( Intent intent )
    {
        showAlertIf( intent );
    }

    public class SampleFragmentPagerAdapter extends FragmentPagerAdapter {
        // final int PAGE_COUNT = 3;
        private Context mContext;

        public SampleFragmentPagerAdapter(FragmentManager fm, Context context) {
            super(fm);
            mContext = context;
        }

        @Override
        public int getCount()
        {
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

    private void showNoPermsAlert( short port )
    {
        PortReg.lookup( this, port, new PortReg.OnHaveAppIDs() {
                @Override
                public void haveAppIDs( String[] appIDs ) {
                    String name = PortReg.nameFor( MainActivity.this, appIDs[0] );
                    final String msg
                        = getString( R.string.alert_msg_need_perms_fmt, name );
                    runOnUiThread( new Runnable() {
                            @Override
                            public void run() {
                                showAlert( AlertType.NEED_PERMS, msg );
                            }
                        } );
                }
            } );
    }

    private void showAlertIf( Intent intent )
    {
        final AlertType type = (AlertType)intent.getSerializableExtra( "TYPE" );
        if ( type != null ) {
            switch( type ) {
            case NOT_GSM:
                showAlert( type, getString(R.string.alert_msg_not_gsm) );
                break;
            case NEED_PERMS:
                short port = intent.getShortExtra( NBSProxy.EXTRA_PORT, (short)-1 );
                showNoPermsAlert( port );
                break;
            }
        }
    }

    private void showAlert( AlertType atyp, String msg )
    {
        new AlertDialog.Builder(this)
            .setMessage( msg )
            .setPositiveButton( android.R.string.ok, null )
            .show();
    }

    private static enum AlertType { NOT_GSM,
                                    NEED_PERMS,
    }


    public static void notifyNoPermissions( Context context, short port )
    {
        AlertType type = AlertType.NEED_PERMS;
        Log.d( TAG, "notifyNoPermissions()" );
        MainActivity me = sSelf;
        if ( me == null || me.isFinishing() ) {
            Intent intent = makeSelfIntent( context, type )
                .putExtra( NBSProxy.EXTRA_PORT, port )
                ;
            postNotification( context, intent, type,
                              R.string.notify_noperm_title,
                              R.string.notify_noperm_body );
        } else {
            me.showNoPermsAlert( port );
        }
    }

    private static Intent makeSelfIntent( Context context, AlertType typ )
    {
        Intent intent = new Intent( context, MainActivity.class )
            .setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP
                       | Intent.FLAG_ACTIVITY_SINGLE_TOP )
            .putExtra( "TYPE", typ )
            ;
        return intent;
    }

    private static void postNotification( Context context, Intent intent, AlertType typ,
                                          int titleID, int bodyID )
    {
        PendingIntent pi = PendingIntent
            .getActivity( context, 1000, intent, PendingIntent.FLAG_ONE_SHOT );

        String channelID = makeChannelID( context );

        Notification notification =
            new NotificationCompat.Builder( context, channelID )
            .setContentIntent( pi )
            .setSmallIcon( R.mipmap.ic_launcher_round )
            .setContentTitle( context.getString( titleID ) )
            .setContentText( context.getString( bodyID ) )
            .build();

        NotificationManager nm = (NotificationManager)
            context.getSystemService( Context.NOTIFICATION_SERVICE );
        nm.notify( typ.ordinal() + ID_OFFSET, notification );
    }

    private static String sChannelID = null;
    private static String makeChannelID( Context context )
    {
        if ( sChannelID == null ) {
            String name = "default";
            if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
                NotificationManager notMgr = (NotificationManager)
                    context.getSystemService( Context.NOTIFICATION_SERVICE );

                NotificationChannel channel = notMgr.getNotificationChannel( name );
                if ( channel == null ) {
                    String channelDescription = context.getString( R.string.notify_channel_desc );
                    channel = new NotificationChannel( name, channelDescription,
                                                       NotificationManager.IMPORTANCE_LOW );
                    channel.enableVibration( true );
                    notMgr.createNotificationChannel( channel );
                }
            }
            sChannelID = name;
        }
        return sChannelID;
    }
}
