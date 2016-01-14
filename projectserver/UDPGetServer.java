/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectserver;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.zip.CRC32;

/**
 *
 * @author Naima
 */
public class UDPGetServer 
{
    private boolean _isTimeout = false; 
    private DatagramSocket _serverSocket;
    private InetAddress _clientAddress;
    private int _clientPort;
    
    
    public UDPGetServer()
    {
        _serverSocket = null;
        _clientAddress = null;
        _clientPort = 0;
    }
    

    //Initiates the udp get server-> returns the udp server address
    public String InitiateUDPServer() 
    {
        try 
        { 
            _serverSocket = new DatagramSocket();//creates udp server in any available port
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
    
    
    //gets acknowledge from client
    private int ReceiveAck(int numOfTimeOut)
    {
        while(true) 
        {
            DatagramPacket getAck = new DatagramPacket( new byte[Utility.PACKETSIZE], Utility.PACKETSIZE );
            try 
            {
                _serverSocket.setSoTimeout(10000);// set the timeout in millisecounds.(10s)
                
                // Receive a packet (blocking)
                _serverSocket.receive(getAck);
                IO.Print("Received Acknowledge");
                byte[] data = getAck.getData();
                /*
                    First 3 bytes for acknowledge data->
                        this contains seq number received by client if successful, 
                        otherwise contains 0
                */
                return GetAckValue(data);
            } 
            catch(java.net.SocketTimeoutException ex)
            { 
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
                ErrorHandler.Print(UDPGetServer.class.getName() ,ex);
                return 0;
            }
        }
    }
    
    
    //Receives packets from client
    private DatagramPacket ReceivePacket(int prevI)
    {
        int i = prevI;
        DatagramPacket packet = new DatagramPacket( new byte[Utility.PACKETSIZE], Utility.PACKETSIZE );
        try 
        {
            _serverSocket.setSoTimeout(10000);
            IO.Print("Receiving packet");
            // Receive a packet (blocking)
            _serverSocket.receive(packet);
        } 
        catch(SocketTimeoutException ex)
        {
            i++;
            IO.Print("Timeout reached..."+i);
            if(i < Utility.TimeOutCounter)
                return ReceivePacket(i);
            else
                _isTimeout = true;
        }
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetServer.class.getName() ,ex);
        }
        return packet;
    }
    
    
    //returns if the timeout occured or not
    public boolean IsTimeOutOccurred()
    {
        return _isTimeout;
    }
    
    
    //Receives the first data from the UDP client and sets _clientAddress
    public void RunUDPServer()
    {
        if(_serverSocket == null)
            return;
        DatagramPacket packet = ReceivePacket(0);
        if(_isTimeout)
            return;
        if(_clientAddress == null &&  _clientPort == 0)//First time getting packet from client
        {
            _clientAddress = packet.getAddress();
            _clientPort = packet.getPort();
        }
        else if(_clientAddress != packet.getAddress() || _clientPort != packet.getPort())//else from some other client->ignore
            return;


        String choice = (new String(packet.getData())).trim();
        IO.Print("Received packet data : "+choice);
        ProcessServerResponse(choice);
    }
    
    /*
    checks clients input if everything is okay or not
    if everything okay initiates the file transfer
    else sends client that it's a bad req
    */
    private void ProcessServerResponse(String choice)
    {
        String[] choices = choice.split(" ");
        if(choices.length!=2)
            SendResponse(0, Utility.NOT_A_VALID_REQ);
        
        byte[] serverResponse;
        if(choices[0].equals("get"))
        {
            serverResponse = GetFileContent(choices[1]);
            IO.Print("file content length: "+serverResponse.length);
            if(IsBadResponse(serverResponse))
                SendResponse(0, serverResponse);
            else
                InitiateFileTransfer(serverResponse);
        }
        else
            SendResponse(0, Utility.NOT_A_VALID_REQ);
    }

    //overloading method for string server response
    private void SendResponse(int packetStartingHeader, String response)
    {
        SendResponse(packetStartingHeader, response.getBytes());
    }
    
    
    //breaks into chunks if needed and processes the header
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
    

    //if file exists, returns the file content or returns the error msg
    private byte[] GetFileContent(String fileName) 
    {
        byte[] response = null;
        if(Utility.IsFile(fileName))
        {
            IO.Print("File Exist");
            response = ReadChunks(fileName);
            IO.Print("response size = "+response.length);
        }
        else
        {
            IO.Print("File not exist");
            response = Utility.NOT_A_VALID_FILE.getBytes();
        }
        return response;
    }
    
    
    //reads the file
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
            ErrorHandler.Print(UDPGetServer.class.getName(), ex);
            return new byte[0];
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetServer.class.getName(), ex);
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
                ErrorHandler.Print(UDPGetServer.class.getName(), ex);
            }
        }
    }
    
    
    //checks if the response is not a good response 
    private boolean IsBadResponse(byte[] response) 
    {
        return (response==Utility.NOT_A_VALID_REQ.getBytes()) || 
               (response==Utility.NOT_A_VALID_FILE.getBytes()) ||
               (response==Utility.TOO_LONG_FILE.getBytes());
    }
    
    
    //sends a packet
    private void SendPacket(byte[] byteArray)
    {
        try 
        { 
            DatagramPacket responsepacket = new DatagramPacket(byteArray,
                    byteArray.length, _clientAddress, _clientPort);
            
            _serverSocket.send(responsepacket);
            
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetServer.class.getName(), ex);
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
            ErrorHandler.Print(UDPGetServer.class.getName() ,ex);
        }
        return 0;
    }
    
    
    //creates a byte array with header, checksum and DATASIZE data
    private byte[] CreatePacketData(int seq, byte[] data)
    {
        int checkSumValue = GetCheckSumValue(data, seq);
        while(checkSumValue == 0)
            checkSumValue = GetCheckSumValue(data, seq);
        
        //creates byte array from int value for header, data length and checksum
        byte[] seqArr = Utility.IntTo3ByteArray(seq);
        byte[] lenArr = Utility.IntTo2ByteArray(data.length);
        byte[] checkSumArr = Utility.IntTo4ByteArray(checkSumValue);
        
        //creates the byte array stream to incorporate all of those byte arrays
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
            ErrorHandler.Print(UDPGetServer.class.getName() ,ex);
        }
        return arr;
    }
    
    
    //Closes the server        
    public void Close()
    {
        _serverSocket.close();
    }

    
    //Initiates the file transfer when file is already read
    private void InitiateFileTransfer(byte[] serverResponse) 
    {
        //Create first packet with info about number of chunks and last chunk size
        int numOfPackets = (int)Math.ceil((double)serverResponse.length/(double)Utility.DATASIZE);
        int lastChunkSize = 
                (serverResponse.length>=(numOfPackets * Utility.DATASIZE)) ? 
                serverResponse.length - (numOfPackets * Utility.DATASIZE) :
                serverResponse.length - ((numOfPackets-1) * Utility.DATASIZE);
        
        byte[] packetNum = Utility.IntTo4ByteArray(numOfPackets);
        byte[] lastChunk = Utility.IntTo3ByteArray(lastChunkSize);
        IO.Print("lastChunkSize = "+lastChunkSize+" from byte = "+Utility.ThreeByteArrayToInt(lastChunk));
        try 
        {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(packetNum);
            outputStream.write(lastChunk);
            byte[] arr = outputStream.toByteArray();
            //sends first packet with file info
            SendResponse(Utility.TrueACK, arr);
            if(_isTimeout)
                return;
            IO.Print("Send first response");
            //now start sending the file content
            SendResponse(1, serverResponse);
            if(_isTimeout)
                return;
            IO.Print("Send second response");
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetServer.class.getName() ,ex);
        }
    }

    //extracts ack value from received data
    private int GetAckValue(byte[] data) 
    {
        byte[] seqb = new byte[3];
        seqb[0] = data[0];
        seqb[1] = data[1];
        seqb[2] = data[2];
        return Utility.ThreeByteArrayToInt(seqb);
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
}
