/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.zip.CRC32;

/**
 *
 * @author Naima
 */
public class UDPGetClient implements IUdpClient 
{
    private boolean _isTimeout = false; 
    
    private DatagramSocket _socket;
    private InetAddress _host;
    private int _port;
    
    private byte[] _serverResponseMsg;
    private final String _userInp;
    
    
    public UDPGetClient(String userInp)
    {
        _userInp = userInp;
    }
    
    
    public void InitiateClient(int port, String hostName)
    {
        //FileReceived = false;
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
    
    
    public boolean IsTimeOutOccurred()
    {
        return _isTimeout;
    }
    
       
    //returns only non-corrupted packet
    private byte[] GetPacket()
    {
        IO.Print("SendDataToServer");
        byte[] res = GetServerResponse(0);
        IO.Print("GetServerResponse");
        if(_isTimeout)
            return new byte[0];
        while(IsCorruptedData(res))//checks if the data's checksum is correct or not
        {
            IO.Print("Data corrupted");
            SendDataToServer(Utility.IntTo3ByteArray(0));
            IO.Print("Send Acknowledge");
            res = GetServerResponse(0);
            if(_isTimeout)
                return new byte[0];
        }
        return res;
    }
    
    //returns each non-corrupted correct seq data
    private byte[] GetFileChunk(int currSeqNum)
    {
        byte[] s = GetPacket();
        if(_isTimeout)
            return new byte[0];
        int seq = GetSeqNum(s);
        while(currSeqNum!=seq)
        {
            if(currSeqNum>seq)//prev chunk
                SendDataToServer(Utility.IntTo3ByteArray(Utility.TrueACK));
            else if(currSeqNum<seq)
                ;//later data->ignore

            s = GetServerResponse(0);
            if(_isTimeout)
                return new byte[0];
            seq = GetSeqNum(s);
        }
        return s;
    }
    
    
    //for last chunk, array might be smaller than the DATASIZE. it gets the actual data size of the current chunk
    private int GetActualDataSize(int seq, int numOfPackets, int lastChunkSize)
    {
        int len = 0;
        if(seq == numOfPackets)
            len = lastChunkSize;
        else
            len = Utility.DATASIZE;
        return len;
    }
    
    
    //Starting point of the put file
    //also processes each chunk to create the file in client side
    public void InitiateFileTransfer()
    {
        IO.Print("InitiateFileTransfer");
        SendDataToServer(_userInp);
        byte[] res = GetPacket();//1st packet for file transfer->contains num of packets
        if(_isTimeout)
            return;
        
        IO.Print("Data not corrupted");
        SendDataToServer(Utility.IntTo3ByteArray(Utility.TrueACK));//send ack
        IO.Print("res = "+res.length);
        int success = GetSeqNum(res);
        if(success == 0)//file not found msg
        {
            _serverResponseMsg = GetServerMsg(res);
            return;
        }
        else if(success == Utility.TrueACK)//file found and so go on
        {
            IO.Print("Insisde if...");
            int numOfPackets = Utility.FourByteArrayToInt(GetServerMsg(res));
            int currSeqNum = 1;
            int lastChunkSize = GetLastChunkSize(res);
            IO.Print("lastChunkSize = "+lastChunkSize);
            byte[] fileBytes = null;
            if(IsZeroSizeFile(numOfPackets, lastChunkSize))//to work with 0 size file
            {
                fileBytes = new byte[0];
                numOfPackets = 1;//going to take atlease one chunk for file
            }
            else
                fileBytes = new byte[((numOfPackets-1)*Utility.DATASIZE)+lastChunkSize];
            
            byte[] s = GetFileChunk(currSeqNum);//file content chunk
            if(_isTimeout)
                return;
            
            int seq = GetSeqNum(s);
            while(seq <= numOfPackets)
            {
                IO.Print("Data is correct : "+seq);
                byte[] serverMsg = GetServerMsg(s);
                int len = GetActualDataSize(seq, numOfPackets, lastChunkSize);
                AddContent(fileBytes, seq, len, serverMsg);
                //str.append(new String(serverMsg));
                SendDataToServer(Utility.IntTo3ByteArray(seq)); 
                currSeqNum++;
                if(currSeqNum > numOfPackets)
                    break;
                 
                s = GetFileChunk(currSeqNum);
                if(_isTimeout)
                    return;
                seq = GetSeqNum(s);
                IO.Print("inside while..."+seq);
            }
            _serverResponseMsg = fileBytes;
        }
        IO.Print("Get Completed");
    }
    
    
    //checks if the checksum from servr and checksum from received data are same or not
    private boolean IsCorruptedData(byte[] data)
    {
        int checkSumData = GetCheckSumData(data);
        IO.Print("checkSumData = "+checkSumData);
        int createdCheckSum = GetCreatedCheckSum(data);
        return createdCheckSum != checkSumData;
    }
    
    
    //creates checksum from received data
    private int GetCreatedCheckSum(byte[] data)
    {
        byte[] serverMsg = GetServerMsg(data);
        byte[] seq = Utility.IntTo3ByteArray(GetSeqNum(data));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try 
        {
            outputStream.write(seq);
            outputStream.write(serverMsg);
            byte[] arr = outputStream.toByteArray();
            
            //creates checksum
            int createdCheckSum = CreateCheckSumData(arr);
            return createdCheckSum;
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetClient.class.getName() ,ex);
        }
        return 0;
    }
    
    
    //receives server response
    private byte[] GetServerResponse(int prevI) 
    {
        int i = prevI;
        byte[] b = new byte[Utility.PACKETSIZE];
        try 
        {
            _socket.setSoTimeout(10000);
            // Prepare the packet for receive
            DatagramPacket packet = new DatagramPacket( b, b.length, _host, _port ) ;
            // Wait for a response from the server
            _socket.receive( packet );
            
            return packet.getData();
        }
        catch(SocketTimeoutException ex)
        {
            i++;
            IO.Print("Timeout reached..."+i);
            if(i < Utility.TimeOutCounter)
                return GetServerResponse(i);
            else
            {
                _isTimeout = true;
                return ex.getMessage().getBytes();
            }
        }
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetClient.class.getName(), ex);
            return ex.getMessage().getBytes();
        }
    }
    
    
    //overload method to send string value
    private void SendDataToServer(String b)
    {
        SendDataToServer(b.getBytes());
    }
    
    
    //sends data to server
    private void SendDataToServer(byte[] b)
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
    
    //returns the whole file to the main client
    public byte[] GetFile()
    {
        return _serverResponseMsg;
    }

    
    //extracts seq number from packet
    private int GetSeqNum(byte[] res) 
    {
        byte[] resArray = Utility.GetArray(0, 0+3, res);
        return Utility.ThreeByteArrayToInt(resArray);
    }

    //extracts actual file content from the packet
    private byte[] GetServerMsg(byte[] res)
    {
        int msgLen = Utility.TwoByteArrayToInt(Utility.GetArray(3, 3+2, res));
        return Utility.GetArray(9, 9+msgLen, res);
    }

    //extracts last chunk size from first packet
    private int GetLastChunkSize(byte[] s) 
    {
        return Utility.ThreeByteArrayToInt(Utility.GetArray(13, 13+3, s));
    }

    //extracts checksum data
    private int GetCheckSumData(byte[] s) 
    {
        return Utility.FourByteArrayToInt(Utility.GetArray(5, 5+4, s));
    }
    
    // creates checksum value from an byte array
    private int CreateCheckSumData(byte[] s)
    {
        CRC32 checkSum = new CRC32();
        checkSum.update(s);
        return (int)checkSum.getValue();
    }
    
    
    //closing the socket
    public void Close() 
    {
        _socket.close();
    }

    //adds a small byte array to a bigger one from a specific point to a certain length
    private void AddContent(byte[] fileBytes, int seq, int len, byte[] serverMsg) 
    {
        int startPoint = (seq-1)*Utility.DATASIZE;
        int endPoint = startPoint + len;
        for(int i = startPoint, j = 0; i < endPoint; i++, j++)
        {
            fileBytes[i] = serverMsg[j];
        }
    }

    //checks if this is a zero size file or not
    private boolean IsZeroSizeFile(int numOfPackets, int lastChunkSize) 
    {
        return numOfPackets == 0 && lastChunkSize == 0;
    }
    
}
