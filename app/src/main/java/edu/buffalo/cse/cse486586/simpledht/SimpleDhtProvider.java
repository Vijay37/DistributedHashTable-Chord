package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import static android.content.ContentValues.TAG;

public class SimpleDhtProvider extends ContentProvider {
    private String myPort=""; // Stores my port number
    private String myId=""; // Stores Id generated from genHash
    private String predecessor=""; // Stores information about my current predecessor
    private String successor=""; // Stores information about my current successor
    private String parent_port="5554"; // Info on who is the parent port which accepts node join requests
    static final int SERVER_PORT = 10000;
    static final int port_mul_factor=2; // multiplication factor to get port number from emulator number
    static final String delimiter="`vijayaha`"; // Delimiter string to seperate and identify messages
    static final String request_join="Node-Join"; // Node join message identifier
    static final String key_search="Data-Search"; // Data search message identifier
    static final String key_insert="Data-Insert"; // Key insert message identifier
    static final int timeout=500;
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String key = values.get("key").toString();
        String value = values.get("value").toString();
        Log.v("Key to insert",key);
        Log.v("Value of the given key",value);
        process_insert(key,value);
        return null;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr)));
        Log.v("My Port: ",myPort);
        try {
            myId = genHash(myPort);
            predecessor=myId;
            successor=myId;
            Log.v("My Id : ",myId);
        }catch(Exception e){
            e.printStackTrace();
        }
//        if(!create_server_sock()){
//            Log.e("Oncreate","Failed to create server socket");
//            return false;
//        }
        //send_join_request(); // Send request for node joining into the chord ring
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
        MatrixCursor mc = process_query(selection);
        return mc;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub

        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private boolean create_server_sock(){ // Function to create a server socket to listen to messages from other nodes
        ServerSocket serverSocket=null;
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Can't create a ServerSocket");
            return false;
        }finally {
            if(serverSocket!=null)
                try {
                    serverSocket.close();
                }catch(Exception e1){
                    e1.printStackTrace();
                }

        }
        return true;
    }
    public void send_join_request(){ // Function to send node join request
        if(!(myPort.equals(parent_port))){
            Log.v("Node Join","Sending request to parent node");
            String msg_request=request_join+delimiter+myPort;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg_request,parent_port);
        }
    }
    public void process_insert(String key,String value){
        Log.v("Insert","Processing insert");
        if(am_i_the_only_node()){
            Log.v("Insert","I am the only node in the ring");
            try{
                FileOutputStream fos = getContext().openFileOutput(key, Context.MODE_PRIVATE);
                fos.write(value.getBytes());
                fos.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        else{
            // calculate genhash value and process it as per the chord
        }
    }
    public MatrixCursor process_query(String selection){
        Log.v("Query", selection);
        String[] column_names ={"key","value"};
        MatrixCursor mc = new MatrixCursor(column_names);
        if(am_i_the_only_node()) {
            Log.v("Query","I am the only node");
            try {
                FileInputStream fis = getContext().openFileInput(selection);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                String key_value;
                String value = null;
                while ((key_value = br.readLine()) != null) {
                    value = key_value;
                }
                br.close();
                fis.close();
                Log.v("Query value", value);
                mc.newRow().add("key", selection).add("value", value.trim());

                mc.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return mc;
    }
    private boolean am_i_the_only_node(){ // Function which returns if the node is single in the chord ring
        if(myId==predecessor && myId==successor)
            return true;
        else
            return false;
    }
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> { // Server task to accept client connections and process their messages
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String message;
            String priority="";
            Log.v("Server task","created");
            BufferedReader bR=null;
            while(true){
                Socket client_sock=null;
                try {
                    // Source : https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
                    client_sock = serverSocket.accept();
                    Log.v("Message received","\n");
                    try {
                        bR = new BufferedReader(new InputStreamReader(client_sock.getInputStream()));
                        message = bR.readLine();
                        message = message.trim();
                        if(client_sock!=null)
                            client_sock.close();
                    }catch(Exception e1){
                        e1.printStackTrace();
                        if(client_sock!=null)
                            client_sock.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finally {
                    if(bR!=null)
                        try {
                            bR.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                }

            }
        }

    }
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String msgToSend = msgs[0];
            String toPort = msgs[1];
            Socket socket;
            toPort=String.valueOf(Integer.parseInt(toPort)*port_mul_factor); // calulating actual exact port number
            PrintWriter out=null;
            BufferedReader bR=null;
            Log.v("Client task","Message to send : "+msgToSend);
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(toPort)));
                socket.setSoTimeout(timeout);
                out = new PrintWriter(socket.getOutputStream(), true);
                out.println(msgToSend);
                bR = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Read message from the socket
                if(bR==null){
                    // handle when node join fails
                }
            }catch (IOException e){
                e.printStackTrace();
            }catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }
    }
}
