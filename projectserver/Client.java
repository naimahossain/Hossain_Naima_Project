/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectserver;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 *
 * @author Naima
 */
public class Client extends Thread
{
    private final int _name;
    
    private BufferedReader _in;
    private PrintWriter _out;
    private final Socket _client;
    
    
    public Client(Socket client, int n)
    {
        _name = n;
        _client = client;
    }
    
    
    //@Override
    public void run()
    {
        //initializes io for server and socket
        InitSocketIO();
        
        try 
        {
            IO.Print("Waiting for input... "+_name);
            String inputLine;
            //Read a line from the client
            while ((inputLine = _in.readLine()) != null)
            {
                //if closing tag, breaks the loop
                if (IsClientEndingText(inputLine))
                    InterruptThread();
                
                if(Thread.currentThread().isInterrupted())
                {   
                    //closes all io and client socket
                    CloseClient();
                    break;
                }
                    
                //Print the client input to cmd
                IO.Print ("Server: " + _name+ " : " + inputLine);
                
                //Print the processed line to the client
                ProcessClientRequest(inputLine);
            }
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(Client.class.getName(), ex);
        } 
    }
    
    
    //iinitialize the socket io
    private void InitSocketIO()
    {
        try
        {
            //Reads from the socket.
            _in = new BufferedReader( new InputStreamReader( _client.getInputStream())); 
            //writes to socket
            _out = new PrintWriter(_client.getOutputStream(), true); 
        }
        catch(IOException ex)
        {
            ErrorHandler.Print(Client.class.getName(), ex);
        }
    }
    
    
    //checks if the line is the same as the client's ending text 
    private boolean IsClientEndingText(String s)
    {
        return s.equals(Utility.EndingText);
    }
    
    
    //Close readers, writers and client
    private void CloseClient()
    {	
        try
        {
            IO.Print("Closing Client... " + _name);
            _out.close(); 
            _in.close(); 
            _client.close(); 
        }
        catch (IOException ex) 
        {
            ErrorHandler.Print(Client.class.getName(), ex);
        }
    }
    
    
    //interrupts current thread to 
    private void InterruptThread()
    {
        IO.Print("Thread is closing... " + _name);
        /*
        sets the thread interrupt flag, so that higher 
        level interrupt handler will notice this and 
        can handle this thread properly.
        */
        Thread.currentThread().interrupt();
    }

    
    //checks the user input, if its valid process accordingly, otherwise sends back an error msg
    private void ProcessClientRequest(String inputLine) 
    {
        String[] choices = inputLine.split(" ");
        String response;
        if(choices.length<=0)
        {
            response = Utility.CreateTCPResponse(0, Utility.NOT_A_VALID_REQ);
        }
        else if(choices.length == 1 && choices[0].equals(Utility.ServerFileList))//ls
        {
            response = GetDirectoryContent();
        }
        else if(choices.length == 2 && choices[0].equals(Utility.GetFile))//get
        {
            response = ProcessGetRequest(choices[1]);
        }
        else if(choices.length == 2 && choices[0].equals(Utility.PutFile))//put
        {
            response = ProcessPutRequest(choices[1]);
        }
        else
        {
            response = Utility.CreateTCPResponse(0, Utility.NOT_A_VALID_REQ);
        }
        IO.Print(response);
        _out.println(response);
    }
    
    
    //process ls cmd-> each filename is separated by tab
    private String GetDirectoryContent() 
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
    
    
    /*
    for get req, 
    1. checks if the file exists
        -> starts the udp server to initiate the file transfer
        -> sends the port num of udp server to the client
    2. else returns an error msg
    */
    private String ProcessGetRequest(String fileName) 
    {
        if(Utility.IsFile(fileName))
        {
            UDPGetServer udpServer = new UDPGetServer();
            String response = udpServer.InitiateUDPServer();
            IO.Print(response);
            _out.println(response);
            IO.Print("UDP server waiting for packet");
            udpServer.RunUDPServer();
            IO.Print("UDP server closing");
            udpServer.Close();
            return "Processed";
        }
        else
            return Utility.CreateTCPResponse(0, Utility.NOT_A_VALID_FILE);
    }

    
    /*
    for put req,
    1. searches if the file already exists in the server folder.
        ->asks client if client want to override
            -> if client replies anything other than 'y', this method returns a msg saying the client request is already done
    */
    private String ProcessPutRequest(String fileName) 
    {
        if(Utility.IsFile(fileName))
        {
            if(IsOverriedDeclined())
                return Utility.CreateTCPResponse(0, Utility.DONE);
        }
        
        //Client confirmed to put the file, so initiate the udp server
        UDPPutServer udpServer = new UDPPutServer();
        String udpServerAddress = udpServer.InitiateUDPServer();
        IO.Print(udpServerAddress);
        //sends client the port and host of the udp server
        _out.println(udpServerAddress);
        IO.Print("UDP server waiting for packet");
        udpServer.InitiateGetFile();
        IO.Print("UDP client file transfer initiated");
        //if timeout occures in between, server won't write the file in the server directory
        if(udpServer.IsTimeOutOccurred())
        {
            String s = "Timeout occurred. Try again later...";
            IO.Print(s);
            return s;
        }
        //timeout didn't occure, so the whole file already reached.
        //get the whole file content and write in the server directory
        byte[] fileContent = udpServer.GetFile();
        IO.Print("UDP server closing");
        udpServer.Close();//udp server is done with it's job
        IO.Print("Got file content");
        WriteFile(fileName, fileContent);
        IO.Print("Uploaded :)");
        return "Uploaded";
    }

    
    //Writes the file in the server directory->upload->put
    private void WriteFile(String fileName, byte[] fileContent) 
    {
        FileOutputStream out = null;
        FileChannel inChannel = null;
        try 
        {
            out = new FileOutputStream(Utility.DirectoryName+fileName);
            inChannel = out.getChannel();
            FileLock lock = inChannel.tryLock();//lock the file before start writing
            out.write(fileContent);
            out.flush();
            lock.release();//release the lock
            IO.Print("Content written");
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(Client.class.getName(), ex);
        } 
        finally
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
                ErrorHandler.Print(Client.class.getName(), ex);
            }
        }
    }

    
    //If clients want to override the server file
    private boolean IsOverriedDeclined() 
    {
        _out.println(Utility.CreateTCPResponse(0, Utility.EXIST));
        try 
        {
            String clientReply = _in.readLine();
            return !clientReply.equals("y");
        } 
        catch (IOException ex) 
        {
            ErrorHandler.Print(Client.class.getName(), ex);
        }
        return true;//if any error occures in between, don't override
    }
    
}
