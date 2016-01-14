/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectclient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.zip.CRC32;

/**
 *
 * @author Naima
 */
public class UDPPutClient implements IUdpClient
{
    private boolean _isTimeout = false; 
    
    private DatagramSocket _socket;
    private InetAddress _host;
    private int _port;
    
    private final String _fileName;
    
    
    public UDPPutClient(String fileName)
    {
        _fileName = fileName;
    }
    
    //initiates the client
    public void InitiateClient(int port, String hostName)
    {
        _port = port;
        try 
        {
            _host = InetAddress.getByName(hostName) ;
            // Construct the socket
            _socket = new DatagramSocket() ;
        } 
        catch (IOException ex) 
        {
            ErrorHandler.HandleError(UDPGetClient.class.getName(), ex);
        }
        catch (IllegalArgumentException ex) 
        {
            ErrorHandler.Print(UDPGetClient.class.getName(), ex);
        }
    }

    //initiates the file transfer
    public void InitiateFileTransfer() 
    {
        IO.Print("InitiateFileTransfer");
        byte[] fileContent = ReadChunks(_fileName);
        InitiateFileTransfer(fileContent);
        IO.Print("Reading");
    }
    
    //ack receiver
    private int ReceiveAck(int numOfTimeOut)
    {
        while(true) 
        {
            DatagramPacket getAck = new DatagramPacket( new byte[Utility.PACKETSIZE], Utility.PACKETSIZE );
            try 
            {
                _socket.setSoTimeout(10000);// set the timeout in millisecounds.
                
                // Receive a packet (blocking)
                _socket.receive(getAck);
                IO.Print("Received Acknowledge");
                byte[] data = getAck.getData();
                /*
                First 3 bytes for acknowledge data->
                this contains seq number received by client if successful, 
                otherwise contains 0
                */
                byte[] seqb = new byte[3];
                seqb[0] = data[0];
                seqb[1] = data[1];
                seqb[2] = data[2];
                return Utility.ThreeByteArrayToInt(seqb);
            } 
            catch(java.net.SocketTimeoutException ex)
            { 
                // timeout exception.
                IO.Print("Timeout reached!!! " + ex);
                numOfTimeOut++;
                IO.Print("Timeout reached..."+numOfTimeOut);
                if(numOfTimeOut < Utility.TimeOutCounter)
                    return ReceiveAck(numOfTimeOut);
                else
                    _isTimeout = true;
                return 0;
            }
            catch (IOException ex) 
            {
                ErrorHandler.Print(UDPPutClient.class.getName() ,ex);
                return 0;
            }
        }
    }
    


    //returns if the timeout occured or not
    public boolean IsTimeOutOccurred() 
    {
        return _isTimeout;
    }
    
    
    
    //Reads the file from the disc to the memory    
    private byte[]  ReadChunks(String fileName)
    {
        FileInputStream stream = null;
        FileChannel inChannel = null;
        try 
        {
            File file = new File(Utility.DirectoryName+fileName);
            stream = new FileInputStream(file);
            inChannel = stream.getChannel();
            //locks the file before reading
            FileLock lock = inChannel.tryLock(0, Long.MAX_VALUE, true);
            byte[] array = new byte[Utility.DATASIZE];
            int a = stream.read(array);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] arr;    
            IO.Print("Reading file");
            while(true)
            {
                if(a==-1)
                    break;
                IO.Print("a = "+a);
                //keep reading DATASIZE bytes from file and add to the byte array stream
                if(a == Utility.DATASIZE)
                    outputStream.write(array);
                
                //last portion of the file size is smaller than DATASIZE
                //so creates an array of that size with the data and writes that to the byte array stream
                else
                    outputStream.write(Utility.GetArray(0, a, array));
                //Reads datasize byte from the file
                a = stream.read(array);
            }
            
            arr = outputStream.toByteArray();
            
            lock.release();//release the lock
            
            return arr;
        } 
        catch (FileNotFoundException ex) 
        {
            ErrorHandler.Print(UDPPutClient.class.getName(), ex);
            return new byte[0];
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPPutClient.class.getName(), ex);
            return  new byte[0];
        }
        finally
        {
            try 
            {
                if(stream != null)
                    stream.close();
                if(inChannel != null)
                    inChannel.close();
            } 
            catch (IOException ex) 
            {
                ErrorHandler.Print(UDPPutClient.class.getName(), ex);
            }
        }
    }
    
    //closes the socket
    public void Close() 
    {
        _socket.close();
    }

    //initiates file transfer
    private void InitiateFileTransfer(byte[] fileContent) 
    {
        int numOfPackets = (int)Math.floor((double)fileContent.length/(double)Utility.DATASIZE);
        int lastChunkSize = fileContent.length - (numOfPackets * Utility.DATASIZE);
        if(lastChunkSize > 0)
            numOfPackets += 1;
        byte[] packetNum = Utility.IntTo4ByteArray(numOfPackets);
        byte[] lastChunk = Utility.IntTo3ByteArray(lastChunkSize);
        IO.Print("lastChunkSize = "+lastChunkSize+" from byte = "+Utility.ThreeByteArrayToInt(lastChunk));
        try 
        {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(packetNum);
            outputStream.write(lastChunk);
            byte[] arr = outputStream.toByteArray();
            
            SendResponse(Utility.TrueACK, arr);
            if(_isTimeout)
                return;
            IO.Print("Send first response");
            SendResponse(1, fileContent);
            if(_isTimeout)
                return;
            IO.Print("Send second response");
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPPutClient.class.getName() ,ex);
        }
    }
    
       
    
    //creates byte array to send appending header and checksum and sends the array to client
    //also receives clients acknowledgement
    private int CreateAndSendPacket(int start, int end, int header, byte[] actualArray)
    {
        IO.Print("Start = "+start+" End = "+end);
        byte[] data = Utility.GetArray(start, end, actualArray);
        System.out.println(data.length);
        byte[] packetData = CreatePacketData(header, data);
        System.out.println(packetData.length);
        SendPacket(packetData);
        int ack = ReceiveAck(0);
        if(_isTimeout)
            return ack;
        /*
        time out didn't occur but ack is 0.
            ->the file client received was either corrupted or wrong seq. 
            ->try sending data again
        */
        IO.Print("ack = "+ack+" i = "+header);
        if(IsRetransmitNeeded(ack))
            Retransmit(packetData, ack);
        return ack;
    }
    
    
    
    //checks if the retransmimssion needed or not
    private boolean IsRetransmitNeeded(int ack) 
    {//timeout didn't occur but the ack=0->client didn't received the correct packet
        return ack == 0 && !_isTimeout;
    }

    
    //Retransmitting
    private void Retransmit(byte[] packetData, int ack) 
    {
        int timeOutC = 0;
        while(IsRetransmitNeeded(0))
        {
            SendPacket(packetData);
            ack = ReceiveAck(timeOutC);
            timeOutC++;
        }
    }
    
    
    private void SendResponse(int packetStartingHeader, byte[] response)
    {
        int len = response.length;
        int numOfPackets = len/Utility.DATASIZE;
        IO.Print("totalLen = "+len+" numOfPacket = "+numOfPackets+" packetStartingHeader = "+packetStartingHeader);
        
        int i = 0;
        while(numOfPackets>i)
        {
            int ack = CreateAndSendPacket(i*Utility.DATASIZE, (i+1)*Utility.DATASIZE, i+1, response);
            if(_isTimeout)
                return;
            if(ack == (i+1) || ack == Utility.TrueACK) //client received the correct packet, proceed to next packet
                i++;
            IO.Print("Current Seq = "+i);
        }
        //last pack
        IO.Print("start = "+(i*Utility.DATASIZE)+" response len = "+response.length);
        int packetHeader = GetHeader(packetStartingHeader, i+1, numOfPackets);
        CreateAndSendPacket(i*Utility.DATASIZE, len, packetHeader, response);
    }
    
    
    
    /*
    if this data is only one packet
        ->header will be true ack, otherwise header will be seqNum
    */
    private int GetHeader(int prevHeader, int seq, int numOfPackets)
    {
        int packetHeader;
        if(numOfPackets > 0)
            packetHeader = seq;
        else
            packetHeader = prevHeader;
        return packetHeader;
    }
    
    //sends packet
    private void SendPacket(byte[] b)
    {
        try 
        {
            IO.Print("Sending Data");
            DatagramPacket packet = new DatagramPacket( b, b.length, _host, _port ) ;
            // Send it
            _socket.send(packet);
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetClient.class.getName(), ex);
        }
    }
    
    //creates checksum value
    private int GetCheckSumValue(byte[] data, int seq)
    {
        byte[] seqArr = Utility.IntTo3ByteArray(seq);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try 
        {
            outputStream.write(seqArr);
            outputStream.write(data);
            byte[] arr = outputStream.toByteArray();
            
            //creates checksum
            CRC32 checkSum = new CRC32();
            checkSum.update(arr);
            int checkSumValue = (int) checkSum.getValue();
            IO.Print("checkSumValue = "+checkSumValue);
            return checkSumValue;
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPPutClient.class.getName() ,ex);
        }
        return 0;
    }
    
    
    //creates final byte array for packet
    private byte[] CreatePacketData(int seq, byte[] data)
    {
        int checkSumValue = GetCheckSumValue(data, seq);
        IO.Print("checkSumValue = "+checkSumValue);
        byte[] seqArr = Utility.IntTo3ByteArray(seq);
        byte[] lenArr = Utility.IntTo2ByteArray(data.length);
        byte[] checkSumArr = Utility.IntTo4ByteArray(checkSumValue);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] arr = new byte[data.length+9];
        try 
        {
            outputStream.write(seqArr);
            outputStream.write(lenArr);
            outputStream.write(checkSumArr);
            outputStream.write(data);
            arr = outputStream.toByteArray();
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetClient.class.getName() ,ex);
        }
        return arr;
    }
    
}
