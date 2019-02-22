package org.eehouse.android.nbsp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    // This will be required eventually to request permissions.
    @Override
    protected void onCreate( Bundle sis )
    {
        super.onCreate(sis);
        finish();
    }

}
