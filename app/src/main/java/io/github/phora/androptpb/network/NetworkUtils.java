package io.github.phora.androptpb.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by phora on 8/24/15.
 */
public class NetworkUtils {

    private static NetworkUtils nm;

    private Context c;

    public final static String METHOD_POST = "POST";
    public final static String METHOD_PUT = "PUT";
    public final static String METHOD_GET = "GET";
    public final static String METHOD_DELETE = "DELETE";


    public static NetworkUtils getInstance(Context ctxt) {
        if (nm == null) {
            nm = new NetworkUtils(ctxt);
        }
        return nm;
    }

    private NetworkUtils(Context context) {
        c = context;
    }

    public HttpURLConnection openConnection(String server_path, String method) {

        URL url = null;

        try {
            url = new URL(server_path);
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        }

        HttpURLConnection conn;

        try {
            if (isConnectedToInternet(c)) {

                if (url != null) {
                    conn = (HttpsURLConnection) url.openConnection();
                } else {
                    //listen
                    return null;
                }

                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    server_path = loc; //needed?
                    url = new URL(loc);
                }
                conn = (HttpsURLConnection) url.openConnection();

                conn.setRequestMethod(method);
                conn.setUseCaches(false);
                conn.setDoInput(true);
                conn.setDoOutput(true);

                conn.setRequestProperty("User-Agent", "AndroPTPB");
                conn.setRequestProperty("Expect", "100-continue");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + RequestData.boundary);
                //conn.setRequestProperty("connection", "Keep-Alive");

                return conn;
            }
        }
        catch (IOException e) {
            //some error handling
            Log.d("NetworkManager", "Failed uploads: "+e.getMessage());
            return null;
        }

        return null;
    }

    public UploadData getUploadResult(String server_path, boolean is_private, HttpURLConnection conn) throws IOException {
        UploadData output = null;

        //should we flush request manually before going in here?
        conn.getOutputStream().flush();
        InputStream stream = conn.getInputStream();

        if (stream != null) {
            Log.d("NetworkManager", "Got response for uploads, reading now");
            InputStreamReader isr = new InputStreamReader(stream);
            BufferedReader br = new BufferedReader(isr);
            boolean isReading = true;
            String data;

            String token = null;
            String sha1 = null;
            String uuid = null;
            String vanity = null;
            Long sunset = null;

            DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSZZZZZ", Locale.ENGLISH);

            do {
                try {
                    data = br.readLine();

                    if (data != null) {
                        if (token == null && (data.startsWith("long: ") || data.startsWith("short: "))) {
                            if (is_private && data.startsWith("long: ")) {
                                token = data.replaceFirst("long: ", "");
                            }
                            else if (!is_private && data.startsWith("short: ")) {
                                token = data.replaceFirst("short: ", "");
                            }
                        }
                        else if (sha1 == null && data.startsWith("sha1: ")) {
                            sha1 = data.replaceFirst("sha1: ", "");
                        }
                        else if (uuid == null && data.startsWith("uuid: ")) {
                            uuid = data.replaceFirst("uuid: ", "");
                        }
                        else if (vanity == null && data.startsWith("label: ")) {
                            vanity = data.replaceFirst("label: ", "");
                        }
                        else if (sunset == null && data.startsWith("sunset: ")) {
                            try {
                                Date date = fmt.parse(data);
                                sunset = date.getTime() / 1000;
                            }
                            catch (ParseException e) {
                                Log.d("NetworkManager", "Can't parse date: "+e.getMessage());
                                continue;
                            }
                        }
                    }
                    else {
                        if (token != null && sha1 != null && uuid != null) {
                            output = new UploadData(server_path, token, uuid, sha1, is_private, sunset);
                        }
                        isReading = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    isReading = false;
                }
            } while (isReading);

            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d("NetworkManager", "Finished uploads");
        return output;
    }

    //https://github.com/Schoumi/Goblim/blob/master/app/src/main/java/fr/mobdev/goblim/NetworkManager.java
    private boolean isConnectedToInternet(Context context)
    {
        //verify the connectivity
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null)
        {
            NetworkInfo.State networkState = networkInfo.getState();
            if (networkState.equals(NetworkInfo.State.CONNECTED))
            {
                return true;
            }
        }
        return false;
    }

    public UploadData getRedirectResult(String server_path, HttpURLConnection conn) throws IOException {
        UploadData output = null;

        //should we flush request manually before going in here?
        conn.getOutputStream().flush();
        InputStream stream = conn.getInputStream();

        if (stream != null) {
            Log.d("NetworkManager", "Got response for redirect, reading now");
            InputStreamReader isr = new InputStreamReader(stream);
            BufferedReader br = new BufferedReader(isr);
            boolean isReading = true;
            String data;
            String token;

            do {
                try {
                    data = br.readLine();
                    if (data != null) {
                        token = data.replaceFirst(server_path+"/", "");
                        output = new UploadData(server_path, token, null, null, false, null);
                    }
                    else {
                        isReading = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    isReading = false;
                }
            } while (isReading);

            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d("NetworkManager", "Finished submitting redirect");
        return output;
    }
}
