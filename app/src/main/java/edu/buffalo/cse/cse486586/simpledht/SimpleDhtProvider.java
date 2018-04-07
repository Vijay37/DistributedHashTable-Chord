package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import static android.content.ContentValues.TAG;

public class SimpleDhtProvider extends ContentProvider {
    private String myPort=""; // Stores my port number
    private String myId=""; // Stores Id generated from genHash
    private String predecessor=""; // Stores information about my current predecessor
    private String successor=""; // Stores information about my current successor
    private String parent_port="5554"; // Info on who is the parent port which accepts node join requests
    static final int SERVER_PORT = 10000;
    static final int port_mul_factor=2; // multiplication factor to get port number from emulator number
    static final String delimiter="`vh`"; // Delimiter string to separate and identify messages
    static final String newline_delimiter="`nl`";  // Delimiter to separate newline
    static final String other_seperator ="`as`"; // Delimiter to separate all values returned by a node when asked for key *
    static final String request_join="Node-Join"; // Node join message identifier
    static final String key_search="Data-Search"; // Data search message identifier
    static final String key_insert="Data-Insert"; // Key insert message identifier
    static final String pre_send="Predecessor"; // Predecessor identifier
    static final String suc_send="Successor"; // Successor identifier
    static final String key_result="Search-Result"; // identifier to tell node about the result it has queried
    static final String add_key="Add-Key"; // Identifier to tell node to add this key into its hash table (Content provider) used when a new node joins
    static final String del_key="Del-Key"; // Identifier to tell node to delete key
    static final String curr_node_queryall="@"; // Identifier used to query all the keys stored in the current node
    static final String all_node_queryall="*"; // Identifier used to query the entire dht
    private ArrayList<String> my_keys=new ArrayList<String>();
    private ArrayList<String> hashed_ids=new ArrayList<String>();
    private HashMap<String, String> ids_port_map = new HashMap<String, String>() ;
    private ArrayList<String> global_keys=new ArrayList<String>();
    private HashMap<String,String> global_values =new HashMap<>();
    private boolean query_started=false;
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if(am_i_the_only_node()){
            Log.v("Delete","I am the only node :"+selection);
            if(selection.equals("*") || selection.equals("@")){
                delete_my_keys();
                my_keys.clear();
            }
            else{
                delete_key_in_my_node(selection);
                my_keys.remove(selection);
            }
        }
        else{
            if(selection.equals(curr_node_queryall)){
                delete_my_keys();
                my_keys.clear();
            }
            else if(selection.equals(all_node_queryall)){
                delete_my_keys();
                my_keys.clear();
                String forward_delete_msg=del_key+delimiter+selection;
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,forward_delete_msg,successor);
            }
            else
                process_delete_key(selection,myPort);
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
        else{
            hashed_ids.add(myId);
            ids_port_map.put(myId,myPort);
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
            try {
                String key_hash = genHash(key);
                String pre_hash = genHash(predecessor);
                int pre_key_diff = key_hash.compareTo(pre_hash);
                int my_key_diff = key_hash.compareTo(myId);
                int my_pre_diff = myId.compareTo(pre_hash);
                if(pre_key_diff>0 && my_key_diff<=0){  // if the id is between my predessor and me
                    insert_in_my_node(key,value);
                }
                else if(pre_key_diff>0 && my_pre_diff<0){ // If the id is greater than my predessor and I am less than my predessor where end and start meet
                    insert_in_my_node(key,value);
                }
                else if(my_pre_diff<0 && my_key_diff<=0){ // If the id smaller than my node and I am less than my predessor
                    insert_in_my_node(key,value);
                }
                else{ // forward the insert to my successor
                    String forward_insert_msg=key_insert+delimiter+key+delimiter+value;
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,forward_insert_msg,successor);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    public void insert_in_my_node(String key, String value){
        try{
            Log.v("Insert at my node","Key :"+key+" Value:"+value);
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
        else{
            if(selection.equals(curr_node_queryall)){
                Log.v("Query","Querying "+selection+" when I am not the only node");
                String value="";
                for(String key : my_keys) {
                    value=query_my_node(key);
                    mc.newRow().add("key", key).add("value", value.trim());
                }
            }
            else if(selection.equals(all_node_queryall)){
                // write function to get all data
                mc = query_all_nodes(selection);
            }
            else{
                mc=query_other_nodes(selection);
            }
        }
        return mc;
    }
    private MatrixCursor query_all_nodes(String selection){ // Querying all nodes
        String[] column_names ={"key","value"};
        MatrixCursor mc = new MatrixCursor(column_names);
        try{
            String value="";
            for(String key: my_keys){
                value=query_my_node(key);
                mc.newRow().add("key", key).add("value", value.trim());
            }
            String forward_search_msg = key_search + delimiter + selection+delimiter+myPort;
            query_started=true;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,forward_search_msg,successor);
            synchronized (global_keys){
                while(query_started){
                    global_keys.wait();
                }
                for(String key : global_keys){
                    value = global_values.get(key);
                    mc.newRow().add("key", key).add("value", value.trim());
                }
                global_keys.clear();// resetting global_keys
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return mc;
    }
    private MatrixCursor query_other_nodes(String selection){
        String[] column_names ={"key","value"};
        MatrixCursor mc = new MatrixCursor(column_names);
        try {
            String key_hash = genHash(selection);
            String pre_hash = genHash(predecessor);
            String value;
            int pre_key_diff = key_hash.compareTo(pre_hash);
            int my_key_diff = key_hash.compareTo(myId);
            int my_pre_diff = myId.compareTo(pre_hash);

            if (pre_key_diff > 0 && my_key_diff <= 0) {  // if the id is between my predessor and me
                value=query_my_node(selection);
                mc.newRow().add("key", selection).add("value", value.trim());
            } else if (pre_key_diff > 0 && my_pre_diff < 0) { // If the id is greater than my predessor and I am less than my predessor where end and start meet
                value=query_my_node(selection);
                mc.newRow().add("key", selection).add("value", value.trim());
            } else if (my_pre_diff < 0 && my_key_diff <= 0) { // If the id smaller than my node and I am less than my predessor
                value=query_my_node(selection);
                mc.newRow().add("key", selection).add("value", value.trim());
            } else { // forward the insert to my successor
                String forward_search_msg = key_search + delimiter + selection+delimiter+myPort;
                query_started=true;
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,forward_search_msg,successor);
                synchronized (global_keys){
                    while(query_started){
                        global_keys.wait();
                    }
                    for(String key : global_keys){
                        value = global_values.get(key);
                        mc.newRow().add("key", key).add("value", value.trim());
                    }
                    global_keys.clear();// resetting global_keys
                }
            }
            return mc;
        }catch(Exception e){
            e.printStackTrace();
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void process_delete_key(String key,String from_port){
        try {
            String key_hash = genHash(key);
            String pre_hash = genHash(predecessor);
            int pre_key_diff = key_hash.compareTo(pre_hash);
            int my_key_diff = key_hash.compareTo(myId);
            int my_pre_diff = myId.compareTo(pre_hash);
            if(pre_key_diff>0 && my_key_diff<=0){  // if the id is between my predessor and me
                delete_key_in_my_node(key);
                my_keys.remove(key);
            }
            else if(pre_key_diff>0 && my_pre_diff<0){ // If the id is greater than my predessor and I am less than my predessor where end and start meet
                delete_key_in_my_node(key);
                my_keys.remove(key);
            }
            else if(my_pre_diff<0 && my_key_diff<=0){ // If the id smaller than my node and I am less than my predessor
                delete_key_in_my_node(key);
                my_keys.remove(key);
            }
            else{ // forward the insert to my successor
                String forward_delete_msg=del_key+delimiter+key+delimiter+from_port;
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,forward_delete_msg,successor);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    public void delete_my_keys(){
        Log.v("Delete","Deleting my local keys");
        for(String key : my_keys)
            delete_key_in_my_node(key);
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
                        publishProgress(message);
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

        @Override
        protected void onProgressUpdate(String... values) {
            String message = values[0];
            process_server_msg(message);
        }
        public void process_server_msg(String message){
            String[] format;
            format=message.split(delimiter);
            if(format[0].equals(request_join)){
                process_node_join(format[1]);
            }
            else if(format[0].equals(pre_send)){
                Log.v("Server","Predecessor : "+format[1]);
                if(my_keys.size()!=0)
                    distribute_keys(format[1]);
                predecessor = format[1];

            }
            else if(format[0].equals(suc_send)){
                successor = format[1];
                Log.v("Server","Successor : "+successor);
            }
            else if(format[0].equals(key_insert)){
                process_insert(format[1],format[2]);
            }
            else if(format[0].equals(key_search)){
                process_query_serverside(format[1],format[2]);
            }
            else if(format[0].equals(key_result)){
                if(format.length!=3)
                    process_query_result(format[1],"");
                else
                    process_query_result(format[1],format[2]);
            }
            else if(format[0].equals(add_key)){
                process_distribute_keys(format[1]);
            }
            else if(format[0].equals(del_key)){
                process_delete_serverside(format[1],format[2]);
            }
        }
        public void process_delete_serverside(String selection,String from_port){
            if(selection.equals(all_node_queryall)){
                delete_my_keys();
                if(!from_port.equals(successor)){
                    String forward_delete_msg=del_key+delimiter+selection+delimiter+from_port;
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,forward_delete_msg,successor);
                }
            }
            process_delete_key(selection,from_port);
        }
        public void process_distribute_keys(String keys){
            String splits[]=keys.split(newline_delimiter);
            String key_splits[];
            for(String splitter:splits){
                if(splitter.trim()!="") {
                    key_splits = splitter.split(other_seperator);
                    my_keys.add(key_splits[0]);
                    insert_in_my_node(key_splits[0],key_splits[1]);
                }
            }
        }
        public void distribute_keys(String new_pre){
            try {
                String new_pre_hash = genHash(new_pre);
                String old_pre_hash = genHash(predecessor);
                int my_old_pre_diff = myId.compareTo(old_pre_hash);
                int my_new_pre_diff = myId.compareTo(new_pre_hash);
                String key_hash="";
                ArrayList<String> temp=new ArrayList<String>();
                String dist_key_msg=add_key+delimiter;
                int my_id_key_diff;
                int new_pre_key_diff;
                boolean iam_small=false;
                boolean iwas_small=false;
                String value="";
                if(my_old_pre_diff<0 && my_new_pre_diff <0){ // my new and old pre are greater than me
                    iam_small=true;
                }
                if(my_old_pre_diff<0 && my_new_pre_diff >0){ // my old is greater than me but not new one
                    iwas_small=true;
                }
                for(String key : my_keys){
                    key_hash=genHash(key);
                    my_id_key_diff=key_hash.compareTo(myId);
                    new_pre_key_diff=key_hash.compareTo(new_pre_hash);
                    if(iam_small && new_pre_key_diff<=0){
                        value=query_my_node(key);
                        dist_key_msg+=key+other_seperator+value+newline_delimiter;
                        temp.add(key);
                    }
                    else if(iwas_small && my_id_key_diff>0){
                        value=query_my_node(key);
                        dist_key_msg+=key+other_seperator+value+newline_delimiter;
                        temp.add(key);
                    }
                    else if(new_pre_key_diff<=0){
                        value=query_my_node(key);
                        dist_key_msg+=key+other_seperator+value+newline_delimiter;
                        temp.add(key);
                    }
                }
                for(String key : temp){  // removing keys from my set of keys and deleting them from content provider
                    my_keys.remove(key);
                    delete_key_in_my_node(key);
                }
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,dist_key_msg,new_pre);
            }catch (Exception e){
                e.printStackTrace();
            }

        }
        public void process_query_result(String key, String value){
            synchronized (global_keys) {
                if(!key.equals(all_node_queryall)) {
                    global_keys.add(key);
                    global_values.put(key, value);
                    query_started = false;
                    global_keys.notify();
                }
                else{
                    String[] keys_set=value.split(newline_delimiter);
                    String[] key_line;
                    String recv_port="";
                    for(String splits : keys_set){
                        key_line=splits.split(other_seperator);
                        if(key_line.length>1){
                            global_keys.add(key_line[0]);
                            global_values.put(key_line[0],key_line[1]);
                        }
                        else{
                            recv_port=splits;
                            Log.v("Process Asterisk","Recevied values from :"+recv_port);
                        }
                    }
                    if(recv_port.equals(predecessor)){
                        query_started=false;
                        global_keys.notify();
                    }

                }
            }
        }
        public void process_query_serverside(String key, String from_port){ // function to process received query request
            try {
                if(key.equals(all_node_queryall)){
                    // handle this scenario when asked for all keys
                    String return_msg=key_result+delimiter+key+delimiter;
                    String value="";
                    for(String selection : my_keys){
                        value=query_my_node(selection);
                        return_msg+=selection+ other_seperator +value+newline_delimiter;
                    }
                    return_msg+=myPort;
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, return_msg, from_port); // returning my keys to node who wants it
                    String forward_search_msg = key_search + delimiter + key+delimiter+from_port;
                    if(!successor.equals(from_port))
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, forward_search_msg, successor); // forwarding the search to my successor

                }
                else {
                    String key_hash = genHash(key);
                    String pre_hash = genHash(predecessor);
                    String value = "";
                    int pre_key_diff = key_hash.compareTo(pre_hash);
                    int my_key_diff = key_hash.compareTo(myId);
                    int my_pre_diff = myId.compareTo(pre_hash);
                    if (pre_key_diff > 0 && my_key_diff <= 0) {  // if the id is between my predessor and me
                        send_my_answer(key,from_port);
                    } else if (pre_key_diff > 0 && my_pre_diff < 0) { // If the id is greater than my predessor and I am less than my predessor where end and start meet
                        send_my_answer(key,from_port);
                    } else if (my_pre_diff < 0 && my_key_diff <= 0) { // If the id smaller than my node and I am less than my predessor
                        send_my_answer(key,from_port);
                    } else { // forward the insert to my successor
                        String forward_search_msg = key_search + delimiter + key + delimiter + from_port;
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, forward_search_msg, successor);
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        public void send_my_answer(String key,String to_port){
            String value = query_my_node(key);
            String qu_res_msg = key_result+delimiter+key+delimiter+value;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,qu_res_msg,to_port);
        }
        public void process_node_join(String node_port){
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
