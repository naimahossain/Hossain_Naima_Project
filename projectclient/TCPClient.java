/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 *
 * @author Naima
 */
public class TCPClient 
{
    private Socket _socket;
    private String _host;
    private BufferedReader _in;
    private PrintWriter _out;
    
    private final BufferedReader _userIn;
    
    
    public TCPClient()
    {
        _userIn = new BufferedReader(new InputStreamReader(System.in)); 
    }
    
    //connects the client to the server
    public void Connect(String host, int port)
    {
        _host = host;
        try 
        {
            _socket = new Socket(_host, port);
        } 
        catch (IOException ex) 
        {
            ErrorHandler.HandleError(TCPClient.class.getName(), ex);
        }
        catch (IllegalArgumentException ex) 
        {
            ErrorHandler.HandleError(TCPClient.class.getName(), ex);
        }
    }
    
    //checks if the socket is still connected with server
    public boolean IsConnected()
    {
        return _socket.isConnected();
    }
    
    //Initializes socket io for reading from and writing to server 
    public void InitSocketIO()
    {
        try 
        {    
            //Creates a buffering character-input stream that uses a default-sized(8192 chars) input buffer.
            _in = new BufferedReader(new InputStreamReader( _socket.getInputStream()));
            //Creates a new PrintWriter from an existing OutputStream with automatic line flushing
            _out = new PrintWriter(_socket.getOutputStream(), true);
        } 
        catch (IOException ex) 
        {
            ErrorHandler.HandleError(TCPClient.class.getName(), ex);
        }
    }
    
    //starts communication with server
    public void StartCommunication()
    {
        try 
        {
            String userInput;
            //prompt user for input
            IO.PromptUser();
            //gets input from user
            while ((userInput = _userIn.readLine()) != null)
            {
                IO.Print(userInput);
                //if not valid input continue
                if(!IsValidInput(userInput))
                {
                    IO.Print(Utility.GetWrongInpMsg());
                    IO.PromptUser();
                    continue;
                }
                //check if it's the ending text
                if (IsEndingText(userInput))
                {
                    _out.println(userInput);
                    IO.Print("Exiting...");
                    break;
                }
                //valid input but server response not needed->!ls, ?
                if(!IsServerResponseNeeded(userInput))
                {
                    ProcessUserInput(userInput);
                    IO.PromptUser();
                    continue;
                }
                //server response is needed-> so check if server is connected
                if(!IsConnected())//checks if any reason connection is lost or not
                {
                    SocketException ex = new SocketException("Socket is not connected");
                    ErrorHandler.HandleError(TCPClient.class.getName(), ex);
                }
                
                String[] splitted = userInput.split(" ");
                String cmd = splitted[0];
                
                //Write user input to the server
                _out.println(userInput);
                //if ls-> server file list
                //if get->if file exists, udp server address
                //if get->file doesn't exist, error msg
                //if put->if file exists, asks for override
                //if put-> file doesn't exist, udp server address
                String serverReply = _in.readLine();
                //writes at standard output
                IO.Print("server:"+serverReply);
                //if ls ends here
                if(cmd.equals(Utility.ServerFileList))
                {
                    IO.PromptUser();
                    continue;
                }
                String prevUserInp = userInput;
                //if put, and ile exists in the server->ask for override
                if(cmd.equals(Utility.PutFile) && IsAskingForOverride(serverReply))
                {
                    userInput = _userIn.readLine();
                    if(!userInput.equals("y"))
                    {   //for other than 'y', server doesn't need to reply anymore
                        _out.println(userInput);
                        IO.Print("server:"+_in.readLine());
                        IO.PromptUser();
                        continue;
                    }
                    else
                    {   //for 'y' server needs to send the address of the server
                        _out.println(userInput);
                        serverReply = _in.readLine();
                    }
                }
                if(IsFileTransferNeeded(serverReply))
                {
                    boolean successful = ProcessFileTransfer(prevUserInp, serverReply);
                    serverReply = _in.readLine();
                    IO.Print("server:"+serverReply);
                }
                //prompt user for input    
                IO.PromptUser();
            }
        } 
        catch (IOException ex) 
        {
            ErrorHandler.HandleError(TCPClient.class.getName(), ex);
        }
    }
    
    
    //Checks if server is asking for override
    private boolean IsAskingForOverride(String serverReply)
    {
        String[] splitted = serverReply.split(",");
        if(splitted.length != 2)
            return false;
        try
        {
            int success = Integer.parseInt(splitted[0]);
            if(success == 0 && splitted[1].endsWith("(y/n)?"))
                return true;
        }
        catch(NumberFormatException ex)
        {
            return false;
        }
        return false;
    }
    
    
    private boolean IsSingleWordCmd(String cmd)
    {
        return ((cmd.equals(Utility.ClientFileList))||
                (cmd.equals(Utility.ServerFileList))||
                (cmd.equals(Utility.EndingText))||
                (cmd.equals(Utility.Help)));
    }
    
    private boolean IsValidInput(String s)
    {
        String[] splitted = s.split(" ");
        if(splitted.length<1)
            return false;
        String cmd = splitted[0];
        if(splitted.length == 1 && IsSingleWordCmd(cmd))
            return true;
        if(splitted.length == 1)//is not ls ot !ls, still len = 1
            return false;
        //for put, if the file doesn't exist in client's directory, it's an invalid input
        if(cmd.equals(Utility.PutFile) && !Utility.IsFileExist(splitted[1]))//file doesn't exist in the clients directory
            return false;
        return (cmd.equals(Utility.GetFile) || cmd.equals(Utility.PutFile)) && splitted.length==2;
    }
    
    //for !ls cmd server response is not needed
    //for get, if user doesn't want to override the file, server response is not needed
    private boolean IsServerResponseNeeded(String s)
    {
        String[] splitted = s.split(" ");
        String cmd = splitted[0];
        if(cmd.equals(Utility.ClientFileList))//!ls
            return false;
        if(cmd.equals(Utility.ServerFileList))
            return true;
        if(cmd.equals(Utility.GetFile) && Utility.IsFileExist(splitted[1]))
        {
            try 
            {
                IO.Print("File already exist. Want to override(y/n)?");
                String userInput = _userIn.readLine();
                if(userInput.equals("y"))
                    return true;
            } 
            catch (IOException ex) 
            {
                ErrorHandler.Print(TCPClient.class.getName(), ex);
                IO.PromptUser();
            }
        }
        if(cmd.equals(Utility.GetFile) && !Utility.IsFileExist(splitted[1]))
            return true;
        if(cmd.equals(Utility.PutFile) && Utility.IsFileExist(splitted[1]))
            return true;
        return false;
    }
    
    //ending text->exit
    private boolean IsEndingText(String s)
    {
        return s.equals(Utility.EndingText);
    }
    
    //Close everything
    public void Close()
    {
        try {
            _out.close();
            _in.close();
            _userIn.close();
            _socket.close();
        } 
        catch (IOException ex) 
        {
            ErrorHandler.HandleError(TCPClient.class.getName(), ex);
        }
    }
    
    //process !ls cmd
    private String GetClientDirectoryFileList() 
    {
        File file = new File(Utility.DirectoryName);
        File[] files = file.listFiles();
        StringBuilder fileNames = new StringBuilder();
        for(File f : files)
        {
            fileNames.append(f.getName());
            fileNames.append("\t");
        }
        return fileNames.toString().trim();
    }
    
    //process client only response user input(!ls, help, wrong input)
    public void ProcessUserInput(String s) 
    {
        if(s.equals(Utility.Help))
            IO.Print(Utility.GetHelpMsg());
        else if(s.equals(Utility.ClientFileList))
            IO.Print(GetClientDirectoryFileList());
        String[] splitted = s.split(" ");
        if(splitted.length ==1)
            return;
        if((splitted.length == 2)&&((splitted[0].equals(Utility.GetFile))||(splitted[0].equals(Utility.PutFile))))
            ;//ignore
        else
            IO.Print(Utility.GetWrongInpMsg());
    }

    //if file transfer is going to happen, server sends a true ack
    private boolean IsFileTransferNeeded(String serverReply) 
    {
        String[] splitted = serverReply.split(",");
        if(splitted.length != 2)
            return false;
        try
        {
            int success = Integer.parseInt(splitted[0]);
            if(success == Utility.TrueACK)
                return true;
        }
        catch(NumberFormatException ex)
        {
            return false;
        }
        return false;
    }
    
    //gets client according to get or put cmd
    private IUdpClient GetClient(String userInp)
    {
        String[] splitted = userInp.split(" ");
        String fileName = splitted[1]; 
        String cmd = splitted[0];
        if(cmd.equals(Utility.GetFile))
            return new UDPGetClient(userInp);
        else 
            return new UDPPutClient(fileName);
    }
    
    //process file transfer
    private boolean ProcessFileTransfer(String userInp, String serverReply) 
    {
        boolean success = true;
        String[] splitted = userInp.split(" ");
        String fileName = splitted[1]; 
        String cmd = splitted[0];
        
        try
        {
            int port = Integer.parseInt(serverReply.split(",")[1]);
            IO.Print("Got port:"+port);
            IUdpClient udpClient = GetClient(userInp);
            udpClient.InitiateClient(port, _host);
            IO.Print("UDP client initialized");
            udpClient.InitiateFileTransfer();
            IO.Print("UDP client file transfer initiated");
            boolean timeout = udpClient.IsTimeOutOccurred();
            if(timeout)
            {
                IO.Print("Timeout occurred. Try again later...");
                success = false;
            }
            udpClient.Close();
            if(cmd.equals(Utility.GetFile) && !timeout)
            {//if get cmd and timeout didn't occur, write the file on the disk
                UDPGetClient udpGetClient = (UDPGetClient)udpClient;
                byte[] fileContent = udpGetClient.GetFile();
                IO.Print("Got file content");
                WriteFile(fileName, fileContent);
                IO.Print("Downloaded :)");
            }
            return success;
        }
        catch(NumberFormatException ex)
        {
            ErrorHandler.Print(TCPClient.class.getName(), ex);
            return false;
        }
    }

    //Write file on the disk
    private void WriteFile(String fileName, byte[] fileContent) 
    {
        FileOutputStream out = null;
        FileChannel inChannel = null;
        try 
        {
            out = new FileOutputStream(Utility.DirectoryName+fileName);
            inChannel = out.getChannel();
            FileLock lock = inChannel.tryLock();//locks the file
            out.write(fileContent);
            out.flush();
            lock.release();
            IO.Print("Content written");
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(UDPGetClient.class.getName(), ex);
        } 
        finally//closes everything
        {
            try 
            {
                if (out != null) 
                    out.close();
                if(inChannel !=null)
                    inChannel.close();
            } 
            catch (IOException ex) 
            {
                ErrorHandler.Print(UDPGetClient.class.getName(), ex);
            }
        }
    }
}
