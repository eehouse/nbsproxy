/* -*- compile-command: "find-and-gradle.sh insXw4Deb"; -*- */
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

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class NBSApp extends Application implements NBSProxy.OnReceived {
    private static final String TAG = NBSApp.class.getSimpleName();
    private static NBSProxy.OnReceived sProc;

    @Override
    public void onCreate()
    {
        super.onCreate();

        NBSProxy.register( this );
    }

    @Override
    public void onDataReceived(Context context, String fromPhone,
                               byte[] data )
    {
        NBSProxy.OnReceived proc = sProc;
        if ( proc != null ) {
            proc.onDataReceived( context, fromPhone, data );
        }
    }

    public static void setNBSCallback( NBSProxy.OnReceived proc )
    {
        sProc = proc;
    }
}
