/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author Naima
 */
public class TCPServer 
{
    private ServerSocket _serverSocket;
    private final int _port; 
    
    public TCPServer(int port)
    {
        this._port = port;
        
    }
    
    //starts the server
    public void StartServer()
    {
        try 
        { 
            _serverSocket = new ServerSocket(_port);
        } 
        catch (IOException  ex) 
        {
            ErrorHandler.HandleError(TCPServer.class.getName() ,ex);
        }
        catch (IllegalArgumentException  ex) 
        {
            ErrorHandler.HandleError(TCPServer.class.getName() ,ex);
        }
        
        for(int i=1; ; i++)
        {
            //accept a new client and start a new thread to handle that client
            Socket client = AcceptClient(i);
            Client newClient = new Client(client, i);
            newClient.start();
        }
    }
    
    //accepts a client
    private Socket AcceptClient(int n)
    {
        Socket client = null;
        IO.Print("Waiting for connection... " + n);
        try 
        {
            client = _serverSocket.accept();
            IO.Print("Accepted Client... " + n);
        } 
        catch (IOException ex)
        {
            ErrorHandler.HandleError(TCPServer.class.getName(), ex);
        }
        IO.Print("Connection successful... " + n);
        return client;
    }
    
    
    //close the server
    public void CloseServer()
    {
        try 
        {
            _serverSocket.close();
        } 
        catch (IOException ex) 
        {
            ErrorHandler.HandleError(TCPServer.class.getName(), ex);
        }
    }
}
