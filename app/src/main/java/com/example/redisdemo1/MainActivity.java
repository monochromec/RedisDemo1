package com.example.redisdemo1;

import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.redisdemo1.R;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPInputStream;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public final class MainActivity extends AppCompatActivity {

    private Jedis mDB;
    private Container mVals;
    private boolean mFirst = true;
    // Create global class instance lock for DB synchronization
    private Semaphore mLock = new Semaphore(0, true);

    // Simple container for holding the image bitmap as well as the image name
    private final class Container {
        private byte [] img;
        private String name;
        public Container (String nP, byte [] iP) {
            img = iP;
            name = nP;
        }
        // Didn't bother with Lombok :-)
        public String getName() {
            return name;
        }
        public byte[] getImg() {
            return img;
        }
    }

    // Show error message if no document is present in DB
    private void showError() {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        build.setTitle(R.string.app_name);
        build.setMessage("Error: no document present in DB")
                .setCancelable(false)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                        // System.exit(-1);
                    }
                });
        build.create().show();
    }

    // Listener class for key change subscription - don't forget to
    // set notify-keyspace-events to at least "Kh" in your redis server config
    //
    // Implementation has to be two-staged thanks to Android's broken design:
    // We get hash value change notification via the channel, in turn release the lock
    // causing the background thread to pull the changed hash values from the redis instance
    private final class ChangeListener extends JedisPubSub {
        @Override
        public void onPMessage(String pat, String chan, String msg) {
            super.onPMessage(pat, chan, msg);
            // Check if notification contains proper key name
            if (chan.contains("keyspace") && chan.split(":") [1].endsWith("fashionPic")) {
                mLock.release();
            }
        }
        @Override
        public void onMessage(String chan, String msg) {
            super.onMessage(chan, msg);
        }
        @Override
        public void onSubscribe(String chan, int subs) {
            super.onSubscribe(chan, subs);
        }
        public void onPSubcribe(String pat, int subs) {
            super.onPSubscribe(pat, subs);
        }
        public void onPUnsubcribe(String pat, int subs) {
            super.onPUnsubscribe(pat, subs);

        }
        public void onUnsubcribe(String chan, int subs) {
            super.onUnsubscribe(chan, subs);
        }
    }


    // Helper class for asynchronous UI updates
    // Implementation is straight forward: wait until the document has been updated
    // (using the instance-wide lock) and then passing on the contents for display
    // in the main UI thread via publishProgress
    // A bit like Hotel California: you can check out any time you like but can never
    // leave... :-)
    private final class WaitForValues extends AsyncTask<Container, Container, Void> {
        @Override
        protected Void doInBackground(Container ... vals) {
            Container cont = null;
            if (mFirst) {
                cont = getValues(mDB);
                publishProgress(cont);
                mFirst = false;
            }
            // Set up keyspace event notification
            mDB.psubscribe(new ChangeListener(), "*keyspace*");
            while (true) {
                try {
                    mLock.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                publishProgress(vals [0]);
            }
        }
        // Display updated document content in main UI thread
        @Override
        protected void onProgressUpdate(Container... cont) {
            super.onProgressUpdate(cont);
            displayContainer(cont [0]);
        }
    }
    // Create seperate thread for broken dialog processing onCreate as dialogs cannot be created
    // here :-(
    private final class WaitForDialog extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void ... p) {
            return null;
        }
        // Display updated document content in main UI thread
        @Override
        protected void onPostExecute(Void p) {
            super.onProgressUpdate(p);
            showError();
        }
    }

    // Set up Redis instance; nothing fancy here as almost verbatim from the Jedis docs
    private Jedis setupDB(String url) {
        Jedis client = null;
        try {
            client = new Jedis(url);
            // client.auth("");
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return client;
    }

    // Get values from the Redis instance using fashionic as the key
    private Container getValues(Jedis conn) {
        List<String> vals = null;
        Container cont = null;
        try {
            vals = conn.hmget("fashionPic", "name", "image");
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        if (vals == null && mFirst) {
            // Create dialog in background if doc is not present
            WaitForDialog dia = new WaitForDialog();
            dia.execute();
            return cont;
        }

        cont = extractValues(vals);
        return cont;
    }

    // Extract bitmap and name into container from document
    // Essentially this means reversing the actions from the server side (decoding
    // and inflating the bitmap string) and finally creating a helper container instance which
    // is returned to the caller
    private Container extractValues(List <String> vals) {
        byte [] bytes = null;
        String name = null;
        try {
            String str0 = vals.get(1);
            name = vals.get(0);
            bytes = str0.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        byte [] imgZ = Base64.decode(bytes, Base64.DEFAULT);
        String img64 = null;
        try {
            GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(imgZ));
            InputStreamReader isr = new InputStreamReader(gis);
            StringWriter sw = new StringWriter();
            char [] chars = new char [64 * 1024];
            for (int len; (len = isr.read(chars, 0, chars.length)) > 0;) {
                sw.write(chars, 0, len);
            }
            img64 = sw.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte [] img = null;
        try {
            img = Base64.decode(img64.getBytes("UTF-8"), Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return new Container(name, img);
    }

    // Simple helper function to display the content of a container:
    // Create ImageView to hold the image bitmap and print the image
    // name below it based on the Textview in the layout in activity_main.xml
    private void displayContainer(Container cont) {
        ImageView view = (ImageView) findViewById(R.id.imageDisplay);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);

        byte [] img = cont.getImg();
        // Standard textbook bitmap creation
        Bitmap bm = BitmapFactory.decodeByteArray(img, 0, img.length);
        view.setImageBitmap(bm);

        TextView text = (TextView) findViewById(R.id.text);
        text.setText(cont.getName());
    }

    // Start the whole thing up:
    // Connecting to the Redis instance and setting up the background thread
    // so that it can pull the initial contents from the hash key and enter
    // the update loop based on any keyspace notification published by the server
    // (note the loop *inside* the
    // doInBackground member function in WaitForValues)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("  Latest Fashion Updates");

        mDB = setupDB("ServerAddress");

        // Enter infinite background loop waiting for doc update via lock in doInBackground in WaitForValues
        WaitForValues task = new WaitForValues();
        task.execute(mVals, mVals);
    }
}
