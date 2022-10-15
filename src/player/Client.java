package player;

//import java.io.BufferedReader;
//import java.io.BufferedWriter;
//import java.io.DataInputStream;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.io.OutputStreamWriter;
import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import player.Controller;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.media.MediaPlayer;



public class Client {
	static final int RPC_REQUEST_SUCCESS = 0;	
	static final String RPC_REQUEST_LISTING = "requestlisting";
	static final String RPC_REQUEST_FILE = "requestfile";
	static final String RPC_REQUEST_MD5 = "requestmd5";
	static final String RPC_REQUEST_ISALIVE = "ping";
	static final String RPC_DISCONNECT = "disconnect";
	
	// Error and sub-codes (Where relevant)	
	static final int SERVER_ERROR = 1;
	
	static final int RPC_ERROR = 2;
	static final int INVALID_COMMAND = 0;
	static final int TOO_FEW_ARGS = 1;
	static final int TOO_MANY_ARGS = 2;
	
	//socket parts
	private SSLSocketFactory socketfact;
	private SSLSocket socket;
	
	//IO readers/writers
	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;	
	private PrintWriter printWriter;
	
	private String buffer;
	private final int PORT_A = 4433;
	private final int PORT_B = 4434;	
	private final String CACHE = "cache/";

	private boolean checksumMatch = true;
	private boolean isConnected = false;
	
	/**
	 * Constructor for Client socket creation
	 * 
	 * @param socketfact
	 */
	public Client(SSLSocketFactory socketfact) {

		/**
		 * Initial connection attempt for sockets and data movers
		 */
		try {
			//create sockets and perform handshake
			
			try {
				
				this.socketfact = socketfact;
				this.socket = (SSLSocket) socketfact.createSocket("localhost", PORT_A);
				socket.startHandshake();
				System.out.println("Socket handshake started");
				
				//create data movers
				this.bufferedReader = new BufferedReader(new InputStreamReader
					(socket.getInputStream()));
				this.bufferedWriter = new BufferedWriter(new OutputStreamWriter
					(socket.getOutputStream()));				
			} catch(ConnectException e) {
				System.out.println("Server is unavailable.");
			} 
			
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error creating client.");
			closeEverything(socket, bufferedReader, bufferedWriter);
		}
		
	}
	
	public String processResponse(BufferedReader bufferedReader) {
		String serverReply = null;
		
		try {
			serverReply = bufferedReader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String[] splitString = serverReply.split(" ");
//		System.out.println("Full string is " + serverReply);
//		if(splitString.length == 2)
//			System.out.println("S[0] = " + splitString[0] + " S[1] = " + splitString[1]);
		
		if(splitString.length == 2) {
			int mainCode = Integer.parseInt(splitString[0].replace("\0", ""));
			int subCode = Integer.parseInt(splitString[1].replace("\0", ""));
			
			switch(mainCode) {
				case RPC_REQUEST_SUCCESS:
					return serverReply;
				case SERVER_ERROR:
					System.out.println("Server failure! Error Code: " + subCode);
					return null;
				case RPC_ERROR:
					switch(subCode) {
						case INVALID_COMMAND:
							System.out.println("Invalid command issued.");
							return null;
						case TOO_FEW_ARGS:
							System.out.println("Too few arguments supplied to command");
							return null;
						case TOO_MANY_ARGS:
							System.out.println("Too many arguments supplied to command");
							return null;
					}
				break;
				default:
					return serverReply;
			}
		} else {
			return serverReply;
		}
		
		return serverReply;
	}
	
	/**
	 * Checks if client is connected.
	 * If false new connection is attempted before returning false 
	 * after connection failure.
	 * 
	 * @return
	 */
	public void verifyConnection(CountDownLatch threadSignal) {
		
		new Thread(new Runnable() {
			public void run() {
				boolean connectionConfirmed = false;
				try {
					printWriter = new PrintWriter(new BufferedWriter(
						new OutputStreamWriter(socket.getOutputStream())));
					
					bufferedReader = new BufferedReader(new InputStreamReader
						(socket.getInputStream()));								
					
					// Send Heartbeat/ISALIVE request to see if connection is still active
					printWriter.print(RPC_REQUEST_ISALIVE + "\0");
					
					
					
					printWriter.flush();
					
					// Look for response, if "pong", then it's alive
					// if null then it's dead.
					buffer = processResponse(bufferedReader);//bufferedReader.readLine();									
					
					if(buffer == null) {
						threadSignal.countDown();
						return;															
					}
					
					System.out.println("Buffer in verify is " + buffer);
					if(buffer != null) {
						System.out.println("Is connected is TRUEE!!!");
						isConnected = true;
						connectionConfirmed = true;
						threadSignal.countDown();
					}				
					
				} catch (IOException e1) {
					e1.printStackTrace();
				} catch (NullPointerException e) {
					System.out.println("Encountered a null socket.");
				}
				threadSignal.countDown();
				
															
				if(!connectionConfirmed) {
					isConnected = false;
					Object obj = new Object();
			
					//if not connected, attempt both ports x5
					try {
						synchronized(obj) {
							System.out.println("Trying to connect to port A");
			
							//try port A
							for(int i = 1; i < 6 && !connectionConfirmed; i++) {
								System.out.println("Port A Attempt " + i);
								
								try {
									socket = (SSLSocket) socketfact.createSocket("localhost", PORT_A);
									socket.startHandshake();
									//create data movers
									bufferedReader = new BufferedReader(new InputStreamReader
										(socket.getInputStream()));
									bufferedWriter = new BufferedWriter(new OutputStreamWriter
										(socket.getOutputStream()));						
									
									isConnected = true;
									connectionConfirmed = true;
//									threadSignal.countDown();
								} catch(SocketException e) {
									System.out.println("Connection failed, server unavailable." + e);						
									isConnected = false;
								} finally {
									try {
										obj.wait(3000);
									} catch (InterruptedException e) {
										System.out.println("Interrupted on wait cycle 1.");
										e.printStackTrace();
									}
								}
			
			//					if(socket != null) {
			//						return true;
			//					}
			
							}
							if(!connectionConfirmed) {
								//try port B
								for(int i = 1; i < 6 && !connectionConfirmed; ++i) {
									System.out.println("Port B Attempt " + i);
									try{
										socket = (SSLSocket) socketfact.createSocket("localhost", PORT_B);
										socket.startHandshake();
										
										//create data movers
										bufferedReader = new BufferedReader(new InputStreamReader
											(socket.getInputStream()));
										bufferedWriter = new BufferedWriter(new OutputStreamWriter
											(socket.getOutputStream()));
										
										isConnected = true;
										connectionConfirmed = true;
//										threadSignal.countDown();
									} catch(SocketException e) {
										System.out.println("Server is unavailable " + e);						
										isConnected = false;
									} finally {
										try {
											obj.wait(1000);
										} catch (InterruptedException e) {
											System.out.println("Interrupted on wait cycle 2.");
											e.printStackTrace();
										}						
									}
								
									
				//					if(socket != null) {
				//						return true;
				//					}
				
								}
							}
						}
					} catch (UnknownHostException e) {
						System.out.println("Error creating client. Unknown host in verifyConnection().");
						e.printStackTrace();
						isConnected = false;
					} catch (IOException e) {
						System.out.println("Error creating client. IO Exception in verifyConnection().");
						e.printStackTrace();
						isConnected = false;
					}	
				}
				
//				threadSignal.countDown();
			}}).start();
	}
	
	/**
	 * Accept list of available filenames from Server
	 * Refreshes every 30 seconds
	 * 
	 * @param mediaList from GUI view.
	 */
	public void receiveListFromServer(ListView<String> finalMediaList) {
		
		Runnable task = () -> {
			Platform.runLater(() -> {
				ObservableList<String> tempMediaList = FXCollections.observableArrayList();
				HashSet<String> allFiles = new HashSet<>();
				File[] cacheFiles = new File(CACHE).listFiles();
				
				CountDownLatch verifyThreadSignal = new CountDownLatch(1);
				
				verifyConnection(verifyThreadSignal);
				
				try {
					verifyThreadSignal.await();
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
				if(this.isConnected) { 
					
					try {
						printWriter = new PrintWriter(new BufferedWriter(
							new OutputStreamWriter(socket.getOutputStream())));
						
						//send RPC
						System.out.println("Sending list request.");
						printWriter.print(RPC_REQUEST_LISTING);
						
						if(printWriter.checkError()) {
							System.err.println("Client: Error writing to socket");
						}
						
						buffer = processResponse(bufferedReader);
											
						if(buffer == null) 
							return;
						

						System.out.println("Sent.");
						
						buffer = "-1";

						String RPC_SUCCESS_STRING = Integer.toString(RPC_REQUEST_SUCCESS);
						while(!buffer.equals(RPC_SUCCESS_STRING)) {
							
							try {
								buffer = bufferedReader.readLine();								
							}catch(IOException e) {
								System.out.println(e);
							}
							
							buffer = buffer.replace("\0", "");
							
							System.out.println("buffer is currently " + buffer);

							
							if(!buffer.equals(RPC_SUCCESS_STRING)) 
								allFiles.add(buffer);																																			
						}

						printWriter.flush();
						
						/**
						 * Add items in cache that may have been removed from server
						 * and remove any duplicates.
						 */
						for (File file : cacheFiles) {
							if (file.isFile()) {
								CountDownLatch threadSignal = new CountDownLatch(1);
								
								System.out.println("Our boolean is " + checksumMatch);
								validateFileToServer(file.getName(), threadSignal);

								try {
									threadSignal.await();
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								
								System.out.println("Our boolean is now " + checksumMatch);
								
								CountDownLatch threadSignal2 = new CountDownLatch(1);
								if(!checksumMatch) {									
									checksumMatch = true;

									receiveMediaFromServer(file.getName(), threadSignal2);
									
									try {
										threadSignal2.await();
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
								
								
								
								allFiles.add(file.getName());
							}
						}
						//System.out.println(allFiles); for testing remove DP
						for(String filename : allFiles) {
							tempMediaList.add(filename);
						}

						//set full list to listView
						finalMediaList.setItems(tempMediaList);
						System.out.println("List retreived.");

					}catch(IOException e) {
						e.printStackTrace();
						System.out.println("Error receiving message from the client");
						closeEverything(socket, bufferedReader, bufferedWriter);
					}
				}
				//DP: Remove once testing complete
				else {
					System.out.println("Server unavailable");
				}
			});
			
		};
		Thread thread = new Thread(task);
		thread.setDaemon(true);
		thread.start();

	}
	
	/**
	 * Sends message to server via String to request a certain file from the list of 
	 * available media.
	 * 
	 * DP:needs placed in controller with scrollbox clickable control
	 * 
	 * @param filename
	 */
	public void sendMediaRequest(String filename) {
		CountDownLatch verifyThreadSignal = new CountDownLatch(1);
		
		verifyConnection(verifyThreadSignal);
		
//		verifyThreadSignal.await();
		if(isConnected) {
			
			try {
				System.out.println("Sending media request RPC_REQUEST_FILE = " + RPC_REQUEST_FILE + " filename= " + filename);
				printWriter = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(
						socket.getOutputStream())));			
				
				printWriter.print(RPC_REQUEST_FILE + " \"" + filename + "\"");
				
				System.out.println("Sent RPC request");
				
				if(printWriter.checkError())
					System.err.println("Client: Error writing to socket");
				
				printWriter.flush();

			}catch(IOException e) {
				e.printStackTrace();
				System.out.println("Error sending message to the client.");
				closeEverything(socket, bufferedReader, bufferedWriter);
			}
		}
	}
	
	public void validateFileToServer(String fileName, CountDownLatch threadSignal) {
//		System.out.println("Running ValidateFileToServer");
		new Thread(new Runnable() {
			
			@Override
			public void run() {		
				
				try {						
					PrintWriter printWriter = new PrintWriter(new BufferedWriter(
						new OutputStreamWriter(
							socket.getOutputStream())));			
					
					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
						socket.getInputStream()));

					
					System.out.println("Running testWriter");
					
					printWriter.print(RPC_REQUEST_MD5 + " \"" + fileName + "\"");
					System.out.println(printWriter.checkError());

//				printWriter.print(RPC_REQUEST_MD5 + " \"" + fileName + "\"");
					String buffer;
//				System.out.println("Attempting to read validation line");
					buffer = processResponse(bufferedReader);
					
					if(buffer == null) {
						threadSignal.countDown();
						return;
					}
					
//				System.out.println("MD5 for " +  fileName + " is " + buffer + " on server");
					
	//			try(InputStream inputStrea)
					MessageDigest md = MessageDigest.getInstance("MD5");
					
					File file = new File("cache/" + fileName);
					String checksum = checksum(md, file);
					
//				System.out.println("Local cache checksum is " + checksum);
					printWriter.flush();
					
					
					checksumMatch = (checksum.equals(buffer));
					System.out.println("Checksum is " + checksumMatch);
					System.out.println("Compared " + checksum + " with " + buffer);
					
					threadSignal.countDown();
					
					
					
					
				} catch(IOException e) {
					System.out.println("IOException encountered " + e);
				} catch (NoSuchAlgorithmException e) {
					System.out.println("NoSuchAlgorithm Exception: " + e);
				}
			}}).start();

	}
	
	private String checksum(MessageDigest digest, File file) {
		FileInputStream fileInputStream = null;
		
		try {
			fileInputStream = new FileInputStream(file);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		
		byte[] byteArray = new byte[1024];
		int bytesCount = 0;
		
		try {
			while((bytesCount = fileInputStream.read(byteArray)) != -1) {
				digest.update(byteArray, 0, bytesCount);
			}
			
			fileInputStream.close();
			
			byte[] bytes = digest.digest();

			StringBuilder sb = new StringBuilder();
			
			for(int i = 0; i < bytes.length; i++) {
				sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
			}
			
			return sb.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	/**
	 * Receive media file from server
	 * 
	 * DP: This needs to be sent file size as well as file name
	 * DP: This is not correct and is currently based on txt messages
	 * @param fileName of sought after media
	 */
	public void receiveMediaFromServer(String fileName, CountDownLatch threadSignal) {	
		new Thread(new Runnable() {

			@Override
			public void run() {
				int size;
				byte[] buffer;
				int read;
				int totalRead = 0;
				int remaining = 0;
				
				CountDownLatch verifyThreadSignal = new CountDownLatch(1);
				

				verifyConnection(verifyThreadSignal);				
				
			    try {			
			    	verifyThreadSignal.await();
				}catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					threadSignal.countDown();
					e1.printStackTrace();
				}
					
				if(isConnected) {
					try {
						System.out.println("Sending media request RPC_REQUEST_FILE = " + RPC_REQUEST_FILE + " filename= " + fileName);
						printWriter = new PrintWriter(new BufferedWriter(
							new OutputStreamWriter(
								socket.getOutputStream())));			
						
						printWriter.print(RPC_REQUEST_FILE + " \"" + fileName + "\"");
						
						System.out.println("Sent RPC request");
						
						if(printWriter.checkError())
							System.err.println("Client: Error writing to socket");
						
						printWriter.flush();						
						
						//initial read of file size
						bufferedReader = new BufferedReader(new InputStreamReader(
							socket.getInputStream()));
						
						
						String strBuffer = processResponse(bufferedReader);
						
						if(strBuffer == null) {
							threadSignal.countDown();
							return;
						}
						
						size = Integer.parseInt(strBuffer);
						
						System.out.println("File Size is: " + size);
						
						//second read for file data
						DataInputStream dis = new DataInputStream(socket.getInputStream());
						FileOutputStream fos = new FileOutputStream("cache/" + fileName);
						
						buffer = new byte[256];				

						remaining = size;
						
						//receive file and store in cache
						while((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
							totalRead += read;
							remaining -= read;
							fos.write(buffer, 0, read);
						}
						
						bufferedReader.readLine();
						
//						bufferedReader.close();
//						fos.close();
						threadSignal.countDown();
					} catch (IOException e) {
						System.out.println("Error making input file streams.");
						e.printStackTrace();
					}	
				} else {
					threadSignal.countDown();
				}
			}
		}).start();
	}
	
	
	/**
	 * Shuts down all readers and sockets upon failure
	 * 
	 * @param socket
	 * @param bufferedReader
	 * @param bufferedWriter
	 */
	public void closeEverything(Socket socket, BufferedReader 
		bufferedReader, BufferedWriter bufferedWriter) {
		try {
			if(bufferedReader != null) {
				bufferedReader.close();
			}
			if(bufferedWriter != null) {
				bufferedWriter.close();
			}
			if(socket != null) {
				socket.close();
			}
			if(this.bufferedReader != null) {
				this.bufferedReader.close();
			}
			if(this.bufferedWriter != null) {
				this.bufferedWriter.close();
			}
			if(this.socket != null) {
				this.socket.close();
			}
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void breakupWithServer() {
		CountDownLatch verifyThreadSignal = new CountDownLatch(1);
		
		verifyConnection(verifyThreadSignal);
		
		try {
			printWriter = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(socket.getOutputStream())));

			printWriter.print(RPC_DISCONNECT);
		} catch(IOException e) {
			System.out.println("Error breaking up with Server.");
		}


	}
	
}
