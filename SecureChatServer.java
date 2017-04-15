import java.util.ArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.SimpleFormatter;
import java.util.logging.XMLFormatter;
import java.io.UnsupportedEncodingException;
import java.io.IOException;

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
public class SecureChatServer {
	private static ArrayList<User> users = new ArrayList<User>();
	private static int port;
	private static String directory;
	private static String title;
	private static final Logger logger = Logger.getLogger(SecureChatServer.class.getName());
	
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
		args = new String[3];
		args[0] = "6800";
		args[1] = "C:/Users/David/Documents/logger"; //please change this in your implementation
		args[2] = "Test SecureChatServer";		
		boolean argsInit = false;
		Handler txtFileHandler = null;
		Handler xmlFileHandler = null;
		Formatter txtFormatter = new SimpleFormatter();
		Formatter xmlFormatter = new XMLFormatter();
		
		try {
			if (args.length != 3) {
				logger.severe("Port and log directory were not specified.");
				throw new IllegalArgumentException("bad_args");
			}
			else argsInit = true;
		}
		catch (IllegalArgumentException i) {
			System.out.println(i.getMessage());
		}
		
		try {
			txtFileHandler = new FileHandler(args[1]);
			txtFileHandler.setFormatter(txtFormatter);
			xmlFileHandler = new FileHandler(args[1] + ".xml");
			xmlFileHandler.setFormatter(xmlFormatter);
			
			logger.addHandler(txtFileHandler);
			logger.addHandler(xmlFileHandler);
			
			txtFileHandler.setLevel(Level.ALL);
			xmlFileHandler.setLevel(Level.ALL);
			logger.setLevel(Level.ALL);
			
			logger.config("Loggers successfully configured.");
		}
		catch (IOException i) {
			logger.log(Level.SEVERE, "An IOException occurred while creating the FileHandler: ", i);
		}
		
		
		if (argsInit == true) {
			port = Integer.parseInt(args[0]);
			directory = args[1];
			title = args[2];
			
			try {
				ServerSocket listener = new ServerSocket(port);
				logger.info("Server initialized on port " + port + ".");
				try {
					while (true) {
						new User(listener.accept()).start();
						logger.fine("Server accepted a socket.");
					}
				}
				catch (Exception e) {
					logger.log(Level.SEVERE, "An exception of type " + e.getClass().toString() + " was encountered in a user thread: ", e);
				}
				finally {
					listener.close();
					logger.info("Server has been closed.");
				}
			}
			catch (BindException b) {
				logger.severe("Couldn't create the server, another service is using the specified port. (" + port + ")");
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
		private static final Logger logger = Logger.getLogger(SecureChatServer.class.getName());
		
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
				try {
					this.send(("TITLE" + title).getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
					logger.warning("UnsupportedEncodingException occurred when converting String to byte[] using UTF-8.");
				}
				catch (Exception e){
					if (e instanceof UnsupportedEncodingException)
						logger.warning("UnsupportedEncodingException occurred when converting String to byte[] using UTF-8 when sending title.");
					else logger.log(Level.SEVERE, "An exception of type " + e.getClass().toString() + " occurred in the user handler for user " + nickname, e);
				}
				while (true) {
					try {
						int temp = socket.getInputStream().read();
						if (temp < 0) {
							logger.fine("InputStream read error on user " + nickname);
							throw new SocketException("Invalid read on user's socket.");
						}
						type = TypeOfData.values()[temp];
						input = new byte[1000];
						data = new byte[socket.getInputStream().read(input)];
					
						for (int i = 0; i < data.length; i++) {
							if (input[i] < 0) {
								logger.fine("InputStream read error on user " + nickname);
								throw new SocketException("Invalid read on user's socket");
							}
							data[i] = input[i];
						}
						
						switch (type) {
							case MESSAGE: 
								if (nicknameSet) {
									logger.info("Message received from user " + nickname + ".");
									data = secureCon.decrypt(data);
									for (User u : users) {
										try {
											u.send(u.secureCon.encrypt((nickname + ',' + new String(data, "UTF-8")).getBytes("UTF-8")), TypeOfData.MESSAGE);
										}
										catch (UnsupportedEncodingException e) {
											logger.warning("UnsupportedEncodingException occurred when converting between byte[] and String using UTF-8.");
										}
										u.send(u.secureCon.encrypt(data), TypeOfData.MESSAGE);
									}
								}
								else {
									try {
										this.send("NO_NICKNAME".getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
										logger.warning("User with no nickname attempted to send a message.");
									}
									catch (UnsupportedEncodingException u) {
										logger.warning("UnsupportedEncodingException occurred when converting String to byte[] using UTF-8.");
									}
								}
								break;
								
							case DH_PUB_KEY: 
								if (nicknameSet) {
									logger.fine("User " + nickname + " issued a public key request.");
									pubKey = secureCon.getPublicKey(data);
									this.send(pubKey, TypeOfData.DH_PUB_KEY);
									secureCon.processOtherPubKey(data);
								}
								else {
									logger.warning("Unnamed user attempted to issue a public key request.");
								}
								break;
								
							case NICKNAME: 
								if (!nicknameSet) {
									if (verifyNickname(new String(data))) {
										nickname = new String(data);
										logger.info("User " + nickname + " joined the server.");
										try {
											for (User u : users) {
												u.send((nickname + " joined the server.").getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
											}
										}
										catch (UnsupportedEncodingException u) {
											logger.warning("UnsupportedEncodingException occurred when converting String to byte[] using UTF-8.");
										}
										nicknameSet = true;
									}
									else {
										try {
											logger.fine("Unnamed user attempted to set invalid nickname.");
											this.send("INVALID_NICKNAME".getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
										}
										catch (UnsupportedEncodingException u) {
											logger.warning("UnsupportedEncodingException occurred when converting String to byte[] using UTF-8.");
										}
									}
								}
								else {
									if (verifyNickname(new String(data))) {
										logger.fine("User " + nickname + " updated their nickname to " + new String(data) + ".");
										try {
											for (User u : users) {
												u.send((nickname + " updated their nickname to " + new String (data) + ".").getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
											}
										}
										catch (UnsupportedEncodingException u) {
											logger.warning("UnsupportedEncodingException occurred when converting String to byte[] using UTF-8.");
										}
										logger.info("User " + nickname + " updated their nickname to " + new String(data) + ".");
										nickname = new String(data);
									}
									else {
										try {
											logger.fine(nickname + " attempted to change their nickname to an invalid nickname.");
											this.send("INVALID_NICKNAME".getBytes("UTF-8"), TypeOfData.SERVER_MESSAGE);
										}
										catch (UnsupportedEncodingException u) {
											logger.warning("UnsupportedEncodingException occurred when converting String to byte[] using UTF-8.");
										}
									}
								}
								break;
								
							default: logger.warning("The server received a malformed message.");
						}
					}
					catch (Exception i) {
						if (i instanceof SocketException) {
							this.alive = false;
							users.remove(this);
							if (nicknameSet) {
								logger.info("User " + nickname + " disconnected from the server.");
								try {
									socket.close();
								}
								catch (IOException e) {
									logger.severe("IOException occurred when attempting to close the socket of user " + nickname);
								}	
							}
							else {
								logger.info("Unnamed user disconnected from the server.");
								try {
									socket.close();
								}
								catch (IOException e) {
									logger.severe("IOException occurred when attempting to close the socket of unnamed user.");
								}	
							}
						}
						else logger.log(Level.SEVERE, "An exception of type " + i.getClass().toString() + " occurred in the user handler for user " + nickname, i);
						return;
					}	
				}
			}
			finally {
				if (alive) {
					users.remove(this);
					if (nicknameSet) {
						logger.info("User " + nickname + " disconnected from the server.");
						try {
							socket.close();
						}
						catch (IOException e) {
							logger.severe("IOException occurred when attempting to close the socket of user " + nickname);
						}	
					}
					else {
						logger.info("Unnamed user disconnected from the server.");
						try {
							socket.close();
						}
						catch (IOException e) {
							logger.severe("IOException occurred when attempting to close the socket of unnamed user.");
						}	
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
	        if (data.length > 1000) {
	        	logger.fine("Attempted to send a message larger than the buffer.");
	        	throw new Exception("Message was too long to be sent.");
	        }

	        try {
		        this.socket.getOutputStream().write(type.ordinal());
		        this.socket.getOutputStream().write(data);
		        this.socket.getOutputStream().flush();
	        }
	        catch (IOException i) {
	        	logger.severe("An IOException occurred while writing bytes to the user's socket.");
	        }
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
			if (nickname.equalsIgnoreCase("SERVER") || nickname.equalsIgnoreCase("ERROR") || nickname.equals(""))
				return false;
			else return true;
		}
	}
}