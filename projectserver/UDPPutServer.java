/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectserver;

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
public class UDPPutServer 
{
    private boolean _isTimeout = false;
    private DatagramSocket _serverSocket;
    private InetAddress _clientAddress;
    private int _clientPort;
    
    private byte[] _fileContent;
    
    
    public UDPPutServer()
    {
        _serverSocket = null;
        _clientAddress = null;
        _clientPort = 0;
    }
    
    //Initiates the udp server and returns the port number of the server
    public String InitiateUDPServer() 
    {
        try 
        { 
            _serverSocket = new DatagramSocket();
            IO.Print("Server is running");
            String port =  String.valueOf(_serverSocket.getLocalPort());
            
            return Utility.CreateTCPResponse(Utility.TrueACK, port);
        } 
        catch (IOException  ex) 
        {
            ErrorHandler.Print(Client.class.getName() ,ex);
            return Utility.CreateTCPResponse(0, Utility.ERROR);
        }
        catch (IllegalArgumentException  ex) 
        {
            ErrorHandler.Print(Client.class.getName() ,ex);
            return Utility.CreateTCPResponse(0, Utility.ERROR);
        }
    }
       
    //returns only non-corrupted packet
    private byte[] GetPacket()
    {
        IO.Print("SendDataToServer");
        byte[] res = GetResponse(0);
        IO.Print("GetServerResponse");
        if(_isTimeout)
            return new byte[0];
        while(IsCorruptedData(res))//checks if the data's checksum is correct or not
        {
            IO.Print("Data corrupted");
            SendData(Utility.IntTo3ByteArray(0));
            IO.Print("Send Acknowledge");
            res = GetResponse(0);
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
                SendData(Utility.IntTo3ByteArray(Utility.TrueACK));
            else if(currSeqNum<seq)
                ;//later data->ignore

            s = GetResponse(0);
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

    //Starting point of the get file
    //also processes each chunk to create the file in server side
    public void InitiateGetFile()
    {
        byte[] res = GetPacket();//1st packet for file transfer->contains num of packets
        if(_isTimeout)
            return;
        
        IO.Print("Data not corrupted");
        SendData(Utility.IntTo3ByteArray(Utility.TrueACK));//send ack
        IO.Print("res = "+res.length);
        int success = GetSeqNum(res);
        if(success == Utility.TrueACK)//file found and so go on
        {
            IO.Print("Insisde if...");
            int numOfPackets = Utility.FourByteArrayToInt(GetMsg(res));
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
            
            byte[] s = GetFileChunk(currSeqNum);//get first file chunk
            if(_isTimeout)
                return;
            int seq = GetSeqNum(s);
            
            while(seq <= numOfPackets)//add each chunk data to the final byte array
            {
                IO.Print("Data is correct : "+seq);
                byte[] serverMsg = GetMsg(s);
                int len = GetActualDataSize(seq, numOfPackets, lastChunkSize);
                AddContent(fileBytes, seq, len, serverMsg);
                SendData(Utility.IntTo3ByteArray(seq)); //send ack
                currSeqNum++;
                if(currSeqNum > numOfPackets)
                    break;
                    
                s = GetFileChunk(currSeqNum);
                if(_isTimeout)
                    return;
                seq = GetSeqNum(s);
                IO.Print("inside while..."+seq);
            }
            _fileContent = fileBytes;
        }
        IO.Print("Put Completed");
    }
    
    //checks if the file is zero size
    private boolean IsZeroSizeFile(int numOfPackets, int lastChunkSize) 
    {
        return numOfPackets == 0 && lastChunkSize == 0;
    }
    
    //returns the file content to the server so that server can write the file in the disk
    public byte[] GetFileContent()
    {
        return _fileContent;
    }
    
    //extracts last chunk size
    private int GetLastChunkSize(byte[] s) 
    {
        return Utility.ThreeByteArrayToInt(Utility.GetArray(13, 13+3, s));
    }
    
    //extracts the actual file content from the packet
    private byte[] GetMsg(byte[] res)
    {
        int msgLen = Utility.TwoByteArrayToInt(Utility.GetArray(3, 3+2, res));
        return Utility.GetArray(9, 9+msgLen, res);
    }
    
    //Adds the content to the byte array
    private void AddContent(byte[] fileBytes, int seq, int len, byte[] serverMsg) 
    {
        int startPoint = (seq-1)*Utility.DATASIZE;
        int endPoint = startPoint + len;
        for(int i = startPoint, j = 0; i < endPoint; i++, j++)
        {
            fileBytes[i] = serverMsg[j];
        }
    }

    //extracts the seq number from the packet
    private int GetSeqNum(byte[] res) 
    {
        byte[] resArray = Utility.GetArray(0, 0+3, res);
        return Utility.ThreeByteArrayToInt(resArray);
    }
    
    //sends data to client
    private void SendData(byte[] b)
    {
        try 
        {
            IO.Print("Sending Data");
            DatagramPacket packet = new DatagramPacket( b, b.length, _clientAddress, _clientPort ) ;
            // Send it
            _serverSocket.send(packet);
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPPutServer.class.getName(), ex);
        }
    }
    
    // checks if the packet's checksum and created checksum is same or not
    private boolean IsCorruptedData(byte[] data)
    {
        int checkSumData = GetCheckSumData(data);
        IO.Print("checkSumData = "+checkSumData);
        int createdCheckSum = GetCreatedCheckSum(data);
        return createdCheckSum != checkSumData;
    }
    
    //extracts checksum data from packet
    private int GetCheckSumData(byte[] s) 
    {
        byte[] checkSumData = Utility.GetArray(5, 5+4, s);
        return Utility.FourByteArrayToInt(checkSumData);
    }
    
    //creates checksum from received data
    private int GetCreatedCheckSum(byte[] s)
    {
        byte[] serverMsg = GetMsg(s);
        byte[] seq = Utility.IntTo3ByteArray(GetSeqNum(s));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try 
        {
            outputStream.write(seq);
            outputStream.write(serverMsg);
            byte[] arr = outputStream.toByteArray();
            
            //creates checksum
            CRC32 checkSum = new CRC32();
            checkSum.update(arr);
            int createdCheckSum = (int)checkSum.getValue();
            return createdCheckSum;
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPPutServer.class.getName() ,ex);
        }
        return 0;
    }
    
    //receives packet 
    private byte[] GetResponse(int prevI) 
    {
        int i = prevI;
        try 
        {
            _serverSocket.setSoTimeout(10000);
            // Prepare the packet for receive
            DatagramPacket packet = new DatagramPacket( new byte[Utility.PACKETSIZE], Utility.PACKETSIZE );
            // Wait for a response from the server
            _serverSocket.receive( packet );
            
            if(IsFirstPacket())
            {
                _clientAddress = packet.getAddress();
                _clientPort = packet.getPort();
            }
            
            return packet.getData();
        } 
        catch(SocketTimeoutException ex)
        {
            i++;
            IO.Print("Timeout reached..."+i);
            if(i < Utility.TimeOutCounter)
                return GetResponse(i);
            else
            {
                _isTimeout = true;
                return ex.getMessage().getBytes();
            }
        }
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPPutServer.class.getName(), ex);
            return ex.getMessage().getBytes();
        }
    }
    
    //closes this udp server
    public void Close() 
    {
        _serverSocket.close();
    }

    //checks if this is the first packet received from the client
    private boolean IsFirstPacket() 
    {
        return _clientAddress==null || _clientPort==0;
    }

    //returns to the main server if timeout occurs
    public boolean IsTimeOutOccurred() 
    {
        return _isTimeout;
    }

    //returns the file content to the main server
    public byte[] GetFile() 
    {
        return _fileContent;
    }
}
