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
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    static final String pre_send="Predecessor"; // Predecessor identifier
    static final String suc_send="Successor"; // Successor identifier
    static final int timeout=500;
    static final String curr_node_queryall="@"; // Identifier used to query all the keys stored in the current node
    static final String all_node_queryall="*"; // Identifier used to query the entire dht
    private ArrayList<String> my_keys=new ArrayList<String>();
    private ArrayList<String> hashed_ids=new ArrayList<String>();
    private HashMap<String, String> ids_port_map = new HashMap<String, String>() ;
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if(am_i_the_only_node()){
            Log.v("Delete","I am the only node");
            if(selection.equals("*") || selection.equals("@")){
                for(String key : my_keys)
                    delete_key_in_my_node(key);
            }
            else{
                delete_key_in_my_node(selection);
            }
        }
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
        if(!create_server_sock()){
            Log.e("Oncreate","Failed to create server socket");
            return false;
        }
        send_join_request(myPort,parent_port); // Send request for node joining into the chord ring
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
        }
        return true;
    }
    public void send_join_request(String sender_port,String to_port){ // Function to send node join request
        if(!(sender_port.equals(parent_port))){
            Log.v("Node Join","Sending node join request to node");
            String msg_request=request_join+delimiter+sender_port;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg_request,to_port);
        }
    }
    public void process_insert(String key,String value){
        Log.v("Insert","Processing insert");
        if(am_i_the_only_node()){
            Log.v("Insert","I am the only node in the ring");
            insert_in_my_node(key,value);
        }
        else{
            // calculate genhash value and process it as per the chord
        }
    }
    public void insert_in_my_node(String key, String value){
        try{
            FileOutputStream fos = getContext().openFileOutput(key, Context.MODE_PRIVATE);
            fos.write(value.getBytes());
            fos.close();
            my_keys.add(key);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public MatrixCursor process_query(String selection){
        Log.v("Query", selection);
        String[] column_names ={"key","value"};
        MatrixCursor mc = new MatrixCursor(column_names);
        if(am_i_the_only_node()) {
            Log.v("Query","I am the only node");
            String value="";
            if(selection.equals(all_node_queryall) || selection.equals(curr_node_queryall)) {
                Log.v("Query","Querying "+selection+" when I am the only node");
                for(String key : my_keys) {
                    value=query_my_node(key);
                    mc.newRow().add("key", key).add("value", value.trim());
                }
            }
            else{
                value=query_my_node(selection);
                mc.newRow().add("key", selection).add("value", value.trim());
            }

            mc.close();
        }
        return mc;
    }
    public String query_my_node(String key){
        String value="";
        try {
            FileInputStream fis = getContext().openFileInput(key);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String key_value;
            while ((key_value = br.readLine()) != null) {
                value = key_value;
            }
            br.close();
            fis.close();


        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }
    public void delete_key_in_my_node(String key){
        try {
            getContext().deleteFile(key);
            my_keys.remove(key);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private boolean am_i_the_only_node(){ // Function which returns if the node is single in the chord ring
        if(myId==predecessor && myId==successor)
            return true;
        else
            return false;
    }
    public void print_my_location(){
        Log.v("About me","MY PORT :"+myPort);
        Log.v("About me","MY PRE :"+predecessor);
        Log.v("About me","MY SUC :"+successor);
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

                    try {
                        bR = new BufferedReader(new InputStreamReader(client_sock.getInputStream()));
                        message = bR.readLine();
                        message = message.trim();
                        Log.v("Server task","message :"+message);
                        process_server_msg(message);
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
        public void process_server_msg(String message){
            String[] format;
            format=message.split(delimiter);
            if(format[0].equals(request_join)){
                process_node_join(format[1]);
            }
            else if(format[0].equals(pre_send)){
                predecessor = format[1];
                Log.v("Server","Predecessor : "+predecessor);
            }
            else if(format[0].equals(suc_send)){
                successor = format[1];
                Log.v("Server","Successor : "+successor);
            }
            print_my_location();
        }
        public void process_node_join(String node_port){
            if(am_i_the_only_node()){
                Log.v("Process node join","I am the only node");
                predecessor = node_port;
                successor = node_port;
                String pre_msg=pre_send+delimiter+myPort;
                String suc_msg=suc_send+delimiter+myPort;
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,pre_msg,node_port);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,suc_msg,node_port);
                try {
                    String hash_id = genHash(node_port);
                    hashed_ids.add(hash_id);
                    hashed_ids.add(myId);
                    ids_port_map.put(myId,myPort);
                    ids_port_map.put(hash_id,node_port);
                }catch(Exception e){
                    e.printStackTrace();
                }

            }
            else{
                try {
                    Log.v("Process node join","Other nodes present in the ring");
                    String new_node_hash = genHash(node_port);
                    Collections.sort(hashed_ids);
                    String prevId=hashed_ids.get(0);
                    boolean loc_found=false; // initialized to check if the location of node is found
                    for(String hId:hashed_ids){
                        if(new_node_hash.compareTo(hId)<0 && new_node_hash.compareTo(prevId)>0){
                            String suc_port=ids_port_map.get(hId);
                            String pre_port=ids_port_map.get(prevId);
                            String pre_msg=pre_send+delimiter+pre_port;
                            String suc_msg=suc_send+delimiter+suc_port;
                            // sending message to new node about its pre and suc
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,pre_msg,node_port);
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,suc_msg,node_port);
                            // Sending messages to its suc and pre
                            pre_msg=pre_send+delimiter+node_port;
                            suc_msg=suc_send+delimiter+node_port;
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,pre_msg,suc_port);
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,suc_msg,pre_port);
                            loc_found=true; // Setting it to true as location is found
                            break;
                        }
                        prevId=hId;
                    }
                    if(!loc_found){
                        Log.v("Node join","Location not found");
                        String firstId=hashed_ids.get(0);
                        String lastId=hashed_ids.get(hashed_ids.size()-1);
                        String suc_port=ids_port_map.get(firstId);
                        String pre_port=ids_port_map.get(lastId);
                        String pre_msg=pre_send+delimiter+pre_port;
                        String suc_msg=suc_send+delimiter+suc_port;
                        // sending message to new node about its pre and suc
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,pre_msg,node_port);
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,suc_msg,node_port);
                        // Sending messages to its suc and pre
                        pre_msg=pre_send+delimiter+node_port;
                        suc_msg=suc_send+delimiter+node_port;
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,pre_msg,suc_port);
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,suc_msg,pre_port);
                    }
                    hashed_ids.add(new_node_hash);
                    ids_port_map.put(new_node_hash,node_port);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
        public void send_node_iam_succ(String node_port){
            String pre_msg=pre_send+delimiter+predecessor;
            String suc_msg=suc_send+delimiter+myPort;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,pre_msg,node_port);
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,suc_msg,node_port);
            // Updating my pre about its new suc and updating my pre to new node
            suc_msg=suc_send+delimiter+node_port;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,suc_msg,predecessor);
            predecessor=node_port;
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
