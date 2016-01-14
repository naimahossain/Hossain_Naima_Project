/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectclient;

/**
 *
 * @author Naima
 */
public class ErrorHandler 
{
    public static void HandleError(String clsName, Exception e)
    {
        Print(clsName, e);
        System.exit(1);
    }
    
    public static void Print(String clsName, Exception e)
    {
        System.err.println(clsName + " : " + e);
    } 
}
