import java.util.ArrayList;
import java.util.Date;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;

import org.apache.commons.io.*;

/**
 * An enumerated type where each member corresponds to the ordinal of the first byte of an incoming message.
 * These represent the different types of communication the user has with the server (e.g. sending a regular message, requesting public keys,
 * indicating that the user is going to disconnect, etc.)
 */
enum TypeOfData
{
	MESSAGE, DH_PUB_KEY, NUM_OF_KEYS, CHATROOM, NICKNAME, DISCONNECT_MESSAGE, CONNECT_MESSAGE
}

/**
 * The first revision of the SecureChatServer implementation. It is very incomplete and missing many features that will be included in the final
 * product, most notably the encrytion and logging. This is meant as a test or proof of concept.
 * 
 * @author David Arena
 */
public class SecureChatServer 
{
	private static ArrayList<User> users;
	private static int port;
	private static String directory;
	private static File logFile;
	private static PrintWriter logger;
	
	/**
	 * The main method used to initialize a server on the desired port. A directory to which logs are sent is also specified.
	 * args[0] is the desired port, args[1] is the desired directory to which chat logs are sent
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception
	{	
		boolean argsInit = false;
		try
		{
			if (args.length != 2)
				throw new IllegalArgumentException("Error: The IP address and chat log directory must be specified in the command line.");
			else argsInit = true;
		}
		catch (IllegalArgumentException i)
		{
			System.out.println(i.getMessage());
		}
		
		if (argsInit == true)
		{
			port = Integer.parseInt(args[0]);
			directory = args[1];
			
			//log file is currently unused
			logFile = new File(directory + "/logs" + port + ".txt");
			logger = null;
			try
			{
				logger = new PrintWriter(logFile);
			}
			catch (FileNotFoundException e)
			{
				System.out.println("Error: Log file could not be created at the specified directory.");
			}
			
			ServerSocket listener = new ServerSocket(port);
			System.out.println("Server initialized on port " + port);
			
			try
			{
				while (true)
				{
					new User(listener.accept()).start();
				}
			}
			finally
			{
				listener.close();
				System.out.println("Server has been closed.");
			}
		}
	}
	
	 /**
	  *  A multithreaded handler class that manages the incoming and outgoing messages of each user.
	  */
	  
	public static class User extends Thread
	{
		private Socket socket;
		private byte[] message;
		private TypeOfData last;
		
		/**
		 * Constructs a handler with the given client socket.
		 * @param mySocket
		 */
				
		public User (Socket mySocket){this.socket = mySocket;}

		/**
		 * Begins a new thread for a user. Continually looks for new messages from the user, then sends them out to all other users currently connected.
		 * Upon user disconnect, removes the user from the connected users list and closes the user's socket.
		 */
		public void run()
		{
			try
			{
				users.add(this);
				
				while (true)
				{
					try
					{
						message = IOUtils.toByteArray(socket.getInputStream());
						if (message.length != 0)
						{
							last = removeFirst(message);
							for (User u : users) {
		                        u.send(message, last);
		                    }
						}
					}
					catch (Exception i)
					{
						System.out.println(i.getMessage());
					}	
				}
			}
			finally
			{
				users.remove(this);
				
				try
				{
					socket.close();
				}
				catch (IOException i)
				{
					System.out.println(i.getMessage());
				}
			}
		}
		
		/**
		 * Sends a byte array over the output stream of the user's socket. The first byte of the array represents the type of the message,
		 * and the remaining bytes represent the contents of the message.
		 * 
		 * @param data
		 * @param type
		 * @throws Exception
		 */
		public void send(byte[] data, TypeOfData type) throws Exception 
		{
	        byte[] toSend = new byte[1 + data.length];
	        toSend[0] = (byte) type.ordinal();
	        for (int i = 1; i < toSend.length; ++i) {
	            toSend[i] = data[i - 1];
	        }
	        this.socket.getOutputStream().write(toSend);
	        this.socket.getOutputStream().flush();
		}
		
		/**
		 * Removes and returns the first byte of an incoming message as a TypeOfData. The first element of the byte array
		 * is truncated, leaving only the data of the message.
		 * 
		 * @param message
		 * @return The type of data representing the message.
		 * @throws IOException
		 */
		public TypeOfData removeFirst(byte[] message) throws IOException
		{
			byte[] temp = new byte[message.length - 1];
			TypeOfData type;
			
			switch (message[0])
			{
				case 0x00: type = TypeOfData.MESSAGE; break;
				case 0x01: type = TypeOfData.DH_PUB_KEY; break;
				case 0x02: type = TypeOfData.NUM_OF_KEYS; break;
				case 0x03: type = TypeOfData.CHATROOM; break;
				case 0x04: type = TypeOfData.NICKNAME; break;
				case 0x05: type = TypeOfData.DISCONNECT_MESSAGE; break;
				case 0x06: type = TypeOfData.CONNECT_MESSAGE; break;
				default: throw new IOException("Error: Malformed message received");
			}
			
			for (int i = 0; i < message.length - 1; i++)
			{
				temp[i] = message[i + 1];
			}
			
			message = temp;
			return type;
		}
	}
}
