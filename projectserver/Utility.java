/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectserver;

import java.io.File;

/**
 *
 * @author Naima
 */
public class Utility 
{
    //reply
    public static final String ERROR = "ERROR OCCURRED. TRY AGAIN...";
    public static final String NOT_A_VALID_REQ = "NOT A VALID REQUEST";
    public static final String NOT_A_VALID_FILE = "NOT A VALID FILE";
    public static final String TOO_LONG_FILE = "FILE IS TOO LONG TO TRANSFER";
    public static final String EXIST = "FILE ALREADY EXIST. WANT TO OVERRIDE(y/n)?";
    public static final String DONE = "FILE TRANSFER NOT REQUIRED";
    //user input
    public static final String ServerFileList = "ls";
    public static final String EndingText = "exit";
    public static final String GetFile = "get";
    public static final String PutFile = "put";   
    //server directory
    public static final String DirectoryName = "./ServerFiles/";
    
    //constants for transferring packets
    public final static int PACKETSIZE = 8*1024;
    public final static int DATASIZE = 8*1024-9;//3 byte = seq, 2 byte = msgLen, 4 byte = checksum, rest server reply
    public final static int TrueACK = 8388607;//(2^23)-1
    public final static int TimeOutCounter = 5;

    
    
    //Checks if the file is a valid file
    public static boolean IsFile(String fileName) 
    {
        File file = new File(DirectoryName+fileName);
        return file.isFile();
    }
    
    //Creates tcp response with ack and msg
    public static String CreateTCPResponse(int success, String msg)
    {
        StringBuilder str = new StringBuilder();
        str.append(success);
        str.append(",");
        str.append(msg);
        return str.toString();
    }
    
    
    //convert integer to byte array length=2
    public static byte[] IntTo2ByteArray(int a) 
    {
        byte[] arr = new byte[2];
        arr[1] = (byte) (a & 0xFF);
        arr[0] = (byte) ((a >> 8) & 0xFF);
        return arr;
    }
   
    //convert byte array length 2 to integer
    public static int TwoByteArrayToInt(byte[] b) 
    {
        return b[1] & 0xFF
                | (b[0] & 0xFF) << 8;
    }
            
    //convert integer to byte array length=3
    public static byte[] IntTo3ByteArray(int a) 
    {
        byte[] arr = new byte[3];
        arr[2] = (byte) (a & 0xFF);
        arr[1] = (byte) ((a >> 8) & 0xFF);
        arr[0] = (byte) ((a >> 16) & 0xFF);
        return arr;
    }
    
    //convert byte array length 3 to integer
    public static int ThreeByteArrayToInt(byte[] b) 
    {
        return b[2] & 0xFF
                | (b[1] & 0xFF) << 8
                | (b[0] & 0xFF) << 16;
    }

    //convert integer to byte array length=4
    public static byte[] IntTo4ByteArray(int a) 
    {
        byte[] arr = new byte[4];
        arr[3] = (byte) (a & 0xFF);
        arr[2] = (byte) ((a >> 8) & 0xFF);
        arr[1] = (byte) ((a >> 16) & 0xFF);
        arr[0] = (byte) ((a >> 24) & 0xFF);
        return arr;
    }
    
    // convert byte array length 4 to integer
    public static int FourByteArrayToInt(byte[] b) 
    {
        return b[3] & 0xFF
                | (b[2] & 0xFF) << 8
                | (b[1] & 0xFF) << 16
                | (b[0] & 0xFF) << 24;
    }
    
    
    //creates a byte array of DATASIZE from the actual data 
    public static byte[] GetArray(int start, int end, byte[] data)
    {
        IO.Print("end-start = "+(end-start));   
        byte[] array = new byte[end-start];
        for(int i = start, j=0; i < end; i++, j++)
            array[j] = data[i];
        return array;
    }
}
