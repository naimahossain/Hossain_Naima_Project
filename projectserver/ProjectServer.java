/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectserver;

/**
 *
 * @author Naima
 */
public class ProjectServer 
{

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    { 
        int port;
        if (args.length != 1)
            port = 4037;//ErrorHandler.HandleError(new IllegalArgumentException("One argument needed"));
        else
            port = Integer.parseInt(args[0]);
        
        
        //Creating server
        TCPServer server = new TCPServer(port);
        server.StartServer();
        server.CloseServer();
    }
    
}
