/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectclient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 *
 * @author Naima
 */
public class Utility 
{
    //reply
    public final static String Not_A_Valid_Inp = "NOT A VALID INPUT";
    //user input
    public final static String EndingText = "exit";
    public final static String ClientFileList = "!ls";
    public final static String ServerFileList = "ls";
    public final static String GetFile = "get";
    public final static String PutFile = "put";
    public final static String Help = "?";
    //client directory
    public final static String DirectoryName = "./ClientFiles/";
    
    //constants for transferring packets
    public final static int PACKETSIZE = 8*1024 ;
    public final static int DATASIZE = 8*1024-9;//3 byte = seq, 2 byte = msgLen, 4 byte = checksum, rest server reply
    public final static int TrueACK =  8388607;//(2^23)-1
    public final static int TimeOutCounter = 5;
    
    
    public static String GetWrongInpMsg()
    { 
        StringBuilder wrongInp = new StringBuilder();
        wrongInp.append(Not_A_Valid_Inp + '\n');
        wrongInp.append("For help: ?");
        return wrongInp.toString();
    }
    
    
    public static String GetHelpMsg()
    {
        StringBuilder helpMsg = new StringBuilder();
        helpMsg.append("To see the file names in server directory: ls\n");
        helpMsg.append("To see the file names in client directory: !ls\n");
        helpMsg.append("To read a file from server: get file_name\n");
        helpMsg.append("To write a file to server: put file_name\n");
        return helpMsg.toString();
    }
    

        
    //extracts particular size array from a big array
    public static byte[] GetArray(int start, int end, byte[] array)
    {
        byte[] resArray = new byte[end-start];
        for(int i=start, j=0; i<end; i++,j++)
        {
            resArray[j] = array[i];
        }
        return resArray;
    }
    
    //returns if the file exists in client directory or not
    public static boolean IsFileExist(String fileName) 
    {
        File file = new File(Utility.DirectoryName+fileName);
        return file.isFile();
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
    
    // method to convert byte array length 4 to integer
    public static int FourByteArrayToInt(byte[] b) 
    {
        return b[3] & 0xFF
                | (b[2] & 0xFF) << 8
                | (b[1] & 0xFF) << 16
                | (b[0] & 0xFF) << 24;
    }
}
