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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import junit.framework.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Record-keeping DB.
 *
 * Let's keep weekly records for now, per client app. So for each week, we'll
 * store total bytes sent, number of sends, number of failures, total bytes
 * received.
 * 
 * Week is just the date modulo the number of units in a week I assume.
 *
 * Let's use a background thread to process new entries to avoid bogging down
 * the UI thread.
 */

public class StatsDB {
    private static final String TAG = StatsDB.class.getSimpleName();
    private static final String TABLE_NAME = "KVPairs";
    private static final String DB_NAME = "nbsp";
    private static final int DB_VERSION = 1;

    /**
     * Class that stores all data for a single appID for a week. If week == 0,
     * it represents all time prior to numbered weeks.
     */
    public static class WeekRecord implements Serializable {
        String appID;
        long bytesTX;
        int countTX;
        long bytesRX;
        int countRX;
        long week;           // weeks since the epoch

        WeekRecord( String appID ) { this.appID = appID; }

        WeekRecord( String appID, boolean isTX, int len )
        {
            this( appID );
            if (isTX) {
                countTX = 1;
                bytesTX = len;
            } else {
                countRX = 1;
                bytesRX = len;
            }
            week = nowAsWeek();
        }

        @Override
        public String toString()
        {
            return String
                .format( "appid: %s, count in: %d, bytes in: %d, count out: %d, bytes out: %d",
                         appID, countRX, bytesRX, countTX, bytesTX );
        }

        /**
         * Produce a key that'll likely not conflict with other uses of a
         * key->value table AND be sortable and express week/appid uniqueness
         */
        String getKey()
        {
            Assert.assertTrue( week > 0 );
            return String.format( "WeekRecord:%d:%s", week, appID );
        }

        static String keyPattern()
        {
            return "WeekRecord:%:%";
        }

        void append( WeekRecord other )
        {
            Assert.assertTrue( appID.equals(other.appID) );
            Assert.assertTrue( week == 0 || week == other.week );
            bytesTX += other.bytesTX;
            countTX += other.countTX;
            bytesRX += other.bytesRX;
            countRX += other.countRX;
        }

        private long nowAsWeek() { return System.currentTimeMillis() / (1000 * 60 * 60 * 24 * 7); }
    }

    private static class DataRequest {
        OnHaveData proc;
        DataRequest( OnHaveData proc ) { this.proc = proc; }
    }

    // Whatever. Just a hack to avoid context in Serializable
    private static class Carrier {
        Object rec;
        Context context;
        Carrier( Context context, Object obj ) { this.context = context; this.rec = obj; }
    }

    public static void record( Context context, boolean isTX, String appID, int datalen )
    {
        WeekRecord rec = new WeekRecord( appID, isTX, datalen );
        Carrier entry = new Carrier( context, rec );
        Log.d( TAG, "record(appID: " + appID + ", in: " + isTX + ", len: " + datalen + ")" );
        sQueue.add( entry );
        startThreadOnce();
    }

    public interface OnHaveData {
        void onHaveData( List<WeekRecord> data );
    }

    public static void getRecords( Context context, OnHaveData proc )
    {
        DataRequest request = new DataRequest( proc );
        sQueue.add( new Carrier( context, request ) );
        startThreadOnce();
    }

    // Something to synchronize on
    private static Thread[] sThreadHolder = {null};
    private static void startThreadOnce()
    {
        synchronized( sThreadHolder ) {
            if ( sThreadHolder[0] == null ) {
                sThreadHolder[0] = new WriterThread();
                sThreadHolder[0].start();
            }
        }
    }

    private static LinkedBlockingQueue<Carrier> sQueue = new LinkedBlockingQueue<>();
    private static class WriterThread extends Thread {
        private DBHelper mDbHelper;
        private SQLiteDatabase mDb;

        @Override
        public void run()
        {
            for ( ; ; ) {
                try {
                    Carrier carrier = sQueue.poll(10, TimeUnit.SECONDS );
                    if ( carrier == null ) {
                        break;
                    }
                    Log.d( TAG, "processing: " + carrier );
                    process( carrier );
                } catch ( InterruptedException ie ) {
                    break;
                }
            }
            
            // when we're done, erase record of ourselves
            synchronized( sThreadHolder ) {
                if ( sThreadHolder[0] == this ) {
                    sThreadHolder[0] = null;
                }
            }
        }

        private void process( Carrier carrier )
        {
            Object obj = carrier.rec;
            if ( obj instanceof DataRequest ) {
                doQuery( carrier.context, (DataRequest)obj );
            } else if ( obj instanceof WeekRecord ) {
                addToTable( carrier.context, (WeekRecord)obj );
            } else {
                Assert.fail();
            }
        }

        private void addToTable( Context context, WeekRecord entry )
        {
            initDB( context );

            // If there's an entry, append. Otherwise create a new one
            String key = entry.getKey();
            WeekRecord curRecord = getRecord( key );
            if ( curRecord != null ) {
                curRecord.append( entry );
            } else {
                curRecord = entry;
            }
            putRecord( curRecord );
        }

        private WeekRecord getRecord( String key )
        {
            WeekRecord result = null;
            String record = get( key );
            if ( record != null ) {
                result = strToRec( record );
            }
            Log.d( TAG, "getRecord(" + key + ") => " + result );
            return result;
        }

        private void putRecord( WeekRecord val )
        {
            try {
                ByteArrayOutputStream bas = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream( bas );
                out.writeObject( val );
                out.flush();
                byte[] bytes = bas.toByteArray();
                String asStr = Base64.encodeToString( bytes, Base64.NO_WRAP );

                String key = val.getKey();
                put( key, asStr );
            } catch ( Exception ex ) {
                Log.e( TAG, ex.getMessage() );
                Assert.assertFalse( BuildConfig.DEBUG );
            }

        }

        private WeekRecord strToRec( String asStr )
        {
            WeekRecord result = null;
            byte[] bytes = Base64.decode( asStr, Base64.NO_WRAP );
            try {
                ObjectInputStream ois =
                    new ObjectInputStream( new ByteArrayInputStream(bytes) );
                result = (WeekRecord)ois.readObject();
            } catch ( Exception ex ) {
                Log.d( TAG, "strToRec(): " + ex.getMessage() );
            }
            return result;
        }

        private String get( String key )
        {
            String result = null;
            String selection = String.format( "%s = '%s'", "KEY", key );
            String[] columns = { "VALUE" };

            Cursor cursor = mDb.query( TABLE_NAME, columns,
                                      selection, null, null, null, null );
            Assert.assertTrue( 1 >= cursor.getCount() );
            int indx = cursor.getColumnIndex( "VALUE" );
            if ( cursor.moveToNext() ) {
                result = cursor.getString( indx );
            }
            cursor.close();
            return result;
        }

        private void put( String key, String val )
        {
            String selection = String.format( "%s = '%s'", "KEY", key );
            ContentValues values = new ContentValues();
            values.put( "VALUE", val );

            long result = mDb.update( TABLE_NAME, values, selection, null );
            if ( 0 == result ) {
                values.put( "KEY", key );
                result = mDb.insert( TABLE_NAME, null, values );
            }
            Log.d( TAG, "put(" + key + ") => " + result );
        }

        private void doQuery( Context context, DataRequest entry )
        {
            initDB( context );

            List<WeekRecord> data = new ArrayList<>();

            String selection = String.format( "KEY LIKE '%s'", WeekRecord.keyPattern() );
            String[] columns = { "KEY", "VALUE" };

            Cursor cursor = mDb.query( TABLE_NAME, columns, selection, null, null, null, null );
            int indxVal = cursor.getColumnIndex( "VALUE" );
            int indxKey = cursor.getColumnIndex( "KEY" );
            while ( cursor.moveToNext() ) {
                String asStr = cursor.getString( indxVal );
                WeekRecord week = strToRec( asStr );
                if ( week == null ) {
                    String key = cursor.getString( indxKey );
                    Log.e( TAG, "tossing week for " + key );
                } else {
                    data.add( week );
                }
            }
            cursor.close();

            entry.proc.onHaveData( data );
        }

        private void initDB( Context context )
        {
            if ( null == mDbHelper ) {
                Assert.assertNotNull( context );
                mDbHelper = new DBHelper( context );
                // force any upgrade
                mDbHelper.getWritableDatabase().close();
                mDb = mDbHelper.getWritableDatabase();
            }
        }
    }

    private static class DBHelper extends SQLiteOpenHelper {

        public DBHelper( Context context )
        {
            super( context, DB_NAME, null, DB_VERSION );
        }
        
        @Override
        public void onCreate( SQLiteDatabase db )
        {
            StringBuilder query =
                new StringBuilder( "CREATE TABLE " )
                .append( TABLE_NAME )
                .append( "(KEY VARCHAR, VALUE VARCHAR);" );

            Log.d( TAG, "making DB: " + query.toString() );
            db.execSQL( query.toString() );
        }

        @Override
        @SuppressWarnings("fallthrough")
        public void onUpgrade( SQLiteDatabase db, int oldVersion, int newVersion )
        {
            Log.i( TAG, "onUpgrade: old: " + oldVersion + "; new: " + newVersion );

            switch( oldVersion ) {
            default:
                db.execSQL( "DROP TABLE " + TABLE_NAME + ";" );
                onCreate( db );
            }
        }
    }
}
