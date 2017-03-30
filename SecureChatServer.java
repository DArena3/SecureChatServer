import java.util.ArrayList;
import java.util.Date;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * An enumerated type where each member corresponds to the ordinal of the first byte of an incoming message.
 * These represent the different types of communication the user has with the server (e.g. sending a regular message, requesting public keys,
 * indicating that the user is going to disconnect, etc.)
 */
enum TypeOfData
{
	MESSAGE, DH_PUB_KEY, NICKNAME, SERVER_MESSAGE
}

/**
 * The first revision of the SecureChatServer implementation. It is very incomplete and missing many features that will be included in the final
 * product, most notably the encrytion and  This is meant as a test or proof of concept.
 * 
 * @author David Arena
 */
public class SecureChatServer 
{
	private static ArrayList<User> users = new ArrayList<User>();
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
		/**
		 * Temporary arguments so the program does not need to be run from the command line
		 */
		args = new String[2];
		args[0] = "6800";
		args[1] = "C:\\Users\\David\\Documents"; //please change this in your implementation
		
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
		private String nickname;
		private byte[] data;
		private byte[] input;
		private TypeOfData type;
		
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
				logger.println("(nickname) joined the server.");
				while (true)
				{
					try
					{
						type = TypeOfData.values()[socket.getInputStream().read()];
						if (type.ordinal() == -1)
							throw new SocketException("Socket has died. RIP");
						input = new byte[1000];
						data = new byte[socket.getInputStream().read(input)];
						
					
						logger.print(new Date() + " ");
						for (int i = 0; i < data.length; i++) 
						{
							if (input[i] == -1)
								throw new SocketException("Socket has died. RIP");
							data[i] = input[i];
						}
						
						System.out.print("(user) sent a message.");
						for (User u : users) 
						{
							u.send(data, type);
						}
						
						logger.println(); 
					}
					catch (Exception i)
					{
						break;
					}	
				}
			}
			finally
			{
				users.remove(this);
				logger.println("nickname disconnected from the server.");
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
	        if (data.length > 1000)
	        	throw new Exception("Error: message was too long to be sent.");

	        this.socket.getOutputStream().write(type.ordinal());
	        this.socket.getOutputStream().write(data);
	        this.socket.getOutputStream().flush();
		}
	}
}