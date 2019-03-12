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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import junit.framework.Assert;

public class PortReg {
    private static final String TAG = PortReg.class.getSimpleName();
    private static final String MAP_KEY = TAG + ".map";

    static void register( final Context context, final short port,
                          final String appID )
    {
        onMapLoaded( context, new Runnable() {
                @Override
                public void run() {
                    HashSet<String> appIDs = sMap.get( port );
                    if ( appIDs == null ) {
                        appIDs = new HashSet<String>();
                        sMap.put( port, appIDs );
                    }

                    if ( !appIDs.contains( appID ) ) {
                        appIDs.add( appID );
                        StatsDB.put( context, MAP_KEY, sMap );
                    }
                }
            } );
    }

    static void unregister( Context context, final short port, String appID )
    {
        onMapLoaded( context, new Runnable() {
                @Override
                public void run() {
                    HashSet<String> appIDs = sMap.get( port );
                    Assert.fail();
                }
            } );
    }

    public interface OnHaveAppIDs {
        void haveAppIDs( String[] appIDs );
    }

    public static void lookup( Context context, final short port,
                               final OnHaveAppIDs proc )
    {
        onMapLoaded( context, new Runnable() {
                @Override
                public void run() {
                    String[] result = {};
                    Set<String> apps = sMap.get( port );
                    if ( apps != null ) {
                        result = apps.toArray( new String[apps.size()] );
                    }
                    proc.haveAppIDs( result );
                }
            } );
    }

    public static String nameFor( Context context, String appID )
    {
        String result = null;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(appID, 0);
            result = info.loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
        }
        return result;
    }

    private static HashMap<Short, HashSet<String>> sMap;
    private static void onMapLoaded( Context context, final Runnable cbck )
    {
        if ( sMap != null ) {
            cbck.run();
        } else {
            StatsDB.get( context, MAP_KEY, new StatsDB.OnHaveSerializable() {
                    @Override
                    public void onHaveData( String key, Serializable datum )
                    {
                        sMap = (HashMap<Short, HashSet<String>>)datum;
                        if ( sMap == null ) {
                            sMap = new HashMap<>();
                        }
                        cbck.run();
                    }
                } );
        }
    }
}
