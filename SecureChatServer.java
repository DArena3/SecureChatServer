import java.util.ArrayList;
import java.util.Date;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.BindException;

/**
 * An enumerated type where each member corresponds to the ordinal of the first byte of an incoming message.
 * These represent the different types of communication the user has with the server (e.g. sending a regular message, requesting public keys,
 * indicating that the user is going to disconnect, etc.)
 */
enum TypeOfData {
	MESSAGE, DH_PUB_KEY, NICKNAME, SERVER_MESSAGE
}

/**
 * The SecureChatServer implementation. It currently contains most of the features necessary, such as encryption and nicknames.
 * 
 * @author David Arena
 */
public class SecureChatServer extends Thread {
	private static ArrayList<User> users = new ArrayList<User>();
	private static int port;
	private static String directory;
	/**
	 * LOGGING IS GETTING A HUGE OVERHAUL: LOGGING CURRENTLY HAS ISSUES AND IS NOT TOP PRIORITY
	 */
	private static File logFile;
	private static PrintWriter logger;
	
	/**
	 * The main method used to initialize a server on the desired port. A directory to which logs are sent is also specified.
	 * args[0] is the desired port, args[1] is the desired directory to which chat logs are sent
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {	
		/**
		 * Temporary arguments so the program does not need to be run from the command line
		 */
		args = new String[2];
		args[0] = "6800";
		args[1] = "C:\\Users\\David\\Documents"; //please change this in your implementation
		
		boolean argsInit = false;
		try {
			if (args.length != 2)
				throw new IllegalArgumentException(new Date() + " " + "<ERROR>: The IP address and chat log directory must be specified in the command line.");
			else argsInit = true;
		}
		catch (IllegalArgumentException i) {
			System.out.println(i.getMessage());
		}
		
		if (argsInit == true) {
			port = Integer.parseInt(args[0]);
			directory = args[1];
			
			logFile = new File(directory + "/logs" + port + ".txt");
			try {
				logger = new PrintWriter(logFile);
			}
			catch (FileNotFoundException e) {
				System.out.println(new Date() + " " + "<ERROR>: Log file could not be created at the specified directory.");
			}
			
			try {
				ServerSocket listener = new ServerSocket(port);
				System.out.println(new Date() + " " + "<SERVER>: Server initialized on port " + port + ".");
				
				try {
					while (true) {
						new User(listener.accept()).start();
					}
				}
				finally {
					listener.close();
					System.out.println(new Date() + " " + "<SERVER>: Server has been closed.");
				}
			}
			catch (BindException b) {
				System.out.println(new Date() + " " + "<ERROR>: There is already a service that is using the specified port.");
			}
		}
	}
	
	 /**
	  *  A multithreaded handler class that manages the incoming and outgoing messages of each user.
	  */
	  
	public static class User extends Thread
	{
		private SecureConnection secureCon = new SecureConnection();
		private Socket socket;
		private String nickname;
		private boolean alive;
		private boolean nicknameSet;
		private byte[] data;
		private byte[] input;
		private byte[] pubKey;
		private TypeOfData type;
		
		/**
		 * Constructs a handler with the given client socket.
		 * @param mySocket
		 */
				
		public User(Socket mySocket){this.socket = mySocket;}
		
		/**
		 * A getter method for the user's nickname. If a nickname for a user is not received by the server, the default nickname is "no_nickname".
		 * @return nickname
		 */
		public String getNickname(){return nickname;}

		/**
		 * Begins a new thread for a user. Continually looks for new messages from the user, then sends them out to all other users currently connected.
		 * Upon user disconnect, removes the user from the connected users list and closes the user's socket.
		 */
		public void run() {
			nicknameSet = false;
			nickname = "no_nickname";
			alive = true;
			try {
				users.add(this);
				logger.println("(nickname) joined the server.");
				while (true) {
					try {
						int temp = socket.getInputStream().read();
						if (temp == -1)
							throw new SocketException("Socket has died. RIP");
						type = TypeOfData.values()[temp];
						input = new byte[1000];
						data = new byte[socket.getInputStream().read(input)];
					
						for (int i = 0; i < data.length; i++) {
							if (input[i] == -1)
								throw new SocketException("Socket has died. RIP");
							data[i] = input[i];
						}
						
						switch (type) {
							case MESSAGE: 
								System.out.println(new Date() + " " + "<" + nickname + ">: (message)");
								logger.println("<" + nickname + ">: (message)");
								for (User u : users) {
									try {
										u.send((nickname).getBytes("UTF-8"), TypeOfData.NICKNAME);
									}
									catch (UnsupportedEncodingException e) {
										e.printStackTrace();
									}
									u.send(data, TypeOfData.MESSAGE);
								}
								break;
								
							case DH_PUB_KEY: 
								System.out.println("<SERVER>: " + nickname + " issued a public key request.");
								pubKey = secureCon.getPublicKey(data);
								this.send(pubKey, TypeOfData.DH_PUB_KEY);
								secureCon.processOtherPubKey(data);
								break;
								
							case NICKNAME: 
								if (nicknameSet == false) {
									if (verifyNickname(new String(data))) {
										nickname = new String(data);
										System.out.println(new Date() + " " + "<SERVER>: " + nickname + " joined the server.");
										logger.println(new Date() + " " + "<SERVER>: " + nickname + " joined the server.");
										try {
											for (User u : users) {
												u.send((nickname + " joined the server.").getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
											}
										}
										catch (UnsupportedEncodingException u) {
											u.printStackTrace();
										}
										nicknameSet = true;
									}
									else {
										try {
											this.send("INVALID_NICKNAME".getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
										}
										catch (UnsupportedEncodingException u) {
											u.printStackTrace();
										}
									}
								}
								else {
									if (verifyNickname(new String(data))) {
										System.out.print(new Date() + " " + "<SERVER>: " + nickname);
										logger.print(new Date() + " " + "<SERVER>: " + nickname);
										try {
											for (User u : users) {
												u.send((nickname + " updated their nickname to " + new String (data) + ".").getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
											}
										}
										catch (UnsupportedEncodingException u) {
											u.printStackTrace();
										}
										nickname = new String(data);
										System.out.println(" updated their nickname to " + nickname + ".");
										logger.println(" updated their nickname to " + nickname + ".");
									}
									else {
										try {
											this.send("INVALID_NICKNAME".getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
										}
										catch (UnsupportedEncodingException u) {
											u.printStackTrace();
										}
									}
								}
								break;
								
							default: throw new IOException(new Date() + " " + "<ERROR>: The server received a malformed message.");
						}
						
						logger.println(); 
					}
					catch (Exception i) {
						if (i instanceof SocketException) {
							this.alive = false;
							users.remove(this);
							System.out.println(new Date() + " " + "<SERVER>: " + nickname + " disconnected from the server (socket lost connection).");
							logger.println(new Date() + " " + "<SERVER>: " + nickname + " disconnected from the server (socket lost connection).");
							try {
								socket.close();
							}
							catch (IOException e) {
								System.out.println(e.getMessage());
							}		
						}
						else i.printStackTrace();
						return;
					}	
				}
			}
			finally {
				if (alive) {
					users.remove(this);
					System.out.println(new Date() + " " + "<SERVER>: " + nickname + " disconnected from the server.");
					logger.println(new Date() + " " + "<SERVER>: " + nickname + " disconnected from the server.");
					try {
						socket.close();
					}
					catch (IOException i) {
						System.out.println(i.getMessage());
					}
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
		public void send(byte[] data, TypeOfData type) throws Exception {
	        if (data.length > 1000)
	        	throw new Exception(new Date() + " " + "<ERROR>: message was too long to be sent.");

	        this.socket.getOutputStream().write(type.ordinal());
	        this.socket.getOutputStream().write(data);
	        this.socket.getOutputStream().flush();
		}
		
		/**
		 * A helper method to verify that a user's submitted nickname is not a reserved nickname or is not currently being used by another user.
		 * @param nickname
		 */
		public boolean verifyNickname(String nickname) {
			for (User u : users) {
				if (nickname.equals(u.getNickname()))
					return false;
			}
			if (nickname.equalsIgnoreCase("SERVER") || nickname.equalsIgnoreCase("ERROR"))
				return false;
			else return true;
		}
	}
}