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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
	
	//socket parts
	private SSLSocketFactory socketfact;
	private SSLSocket socket;
	
	//IO readers/writers
	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;	
	private PrintWriter printWriter;
	
	private String buffer;
	private final int PORT_A = 4433;
	private final int PORT_B = 4433;	

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
			this.socketfact = socketfact;
			this.socket = (SSLSocket) socketfact.createSocket("localhost", PORT_A);
			try {
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
	
	/**
	 * Checks if client is connected.
	 * If false new connection is attempted before returning false 
	 * after connection failure.
	 * 
	 * @return
	 */
	public Boolean verifyConnection() {
		try {
			printWriter = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(socket.getOutputStream())));
			
			this.bufferedReader = new BufferedReader(new InputStreamReader
					(socket.getInputStream()));			
			
			// Send Heartbeat/ISALIVE request to see if connection is still active
			printWriter.print(RPC_REQUEST_ISALIVE + "\0");
			
			printWriter.flush();
			
			// Look for response, if "pong", then it's alive
			// if null then it's dead.
			buffer = bufferedReader.readLine();											
			
			System.out.println("Buffer in verify is " + buffer);
			if(buffer != null)
				return true;
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (NullPointerException e) {
			System.out.println("Encountered a null socket.");
		}
		
		// HOK : Failed attempt at setting a timeout for a thread.
		// Kept it here as it's an interesting example for setting timeouts on threads
		// TODELETE!!!
//		boolean test = false;
//		ExecutorService executorService = Executors.newFixedThreadPool(1);
//		Future<?> future = executorService.submit(new Thread(new Runnable() {
//				@Override
//				public void run() {
//					try {
//						printWriter = new PrintWriter(new BufferedWriter(
//								new OutputStreamWriter(socket.getOutputStream())));
//						
//						//send RPC
//						System.out.println("Sending is alive request");
//						printWriter.print(RPC_REQUEST_ISALIVE);
//						
//						
//						buffer = bufferedReader.readLine();								
//						System.out.println("Received " + buffer);
//						
//						if(buffer.equals("pong")) {
//							System.out.println("Returned the correct value");
//						}
//					}catch(IOException e) {
//						System.out.println(e);
//					}
//				}
//			}));
//		
//		try {
//			future.get(20, TimeUnit.SECONDS);
//		} catch(TimeoutException e) {
//			future.cancel(true);
//			return false;
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		} catch (ExecutionException e) {
//			e.printStackTrace();
//		}
		// TODELETE!!!!
		
		Object obj = new Object();

		//if not connected, attempt both ports x5
		try {
			synchronized(obj) {
				System.out.println("Trying to connect to port A");

				//try port A
				for(int i = 1; i < 6; i++) {
					System.out.println("Port A Attempt " + i);
	
					try {
						this.socket = (SSLSocket) socketfact.createSocket("localhost", PORT_A);
						socket.startHandshake();
						//create data movers
						this.bufferedReader = new BufferedReader(new InputStreamReader
								(socket.getInputStream()));
						this.bufferedWriter = new BufferedWriter(new OutputStreamWriter
								(socket.getOutputStream()));						
						
						return true;
					} catch(SocketException e) {
						System.out.println("Connection failed, server unavailable." + e);						
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
				//try port B
				for(int i = 1; i < 6; ++i) {
					System.out.println("Port B Attempt " + i);
					try{
						this.socket = (SSLSocket) socketfact.createSocket("localhost", PORT_B);
						socket.startHandshake();
						
						//create data movers
						this.bufferedReader = new BufferedReader(new InputStreamReader
								(socket.getInputStream()));
						this.bufferedWriter = new BufferedWriter(new OutputStreamWriter
								(socket.getOutputStream()));
						
						return true;
					} catch(SocketException e) {
						System.out.println("Server is unavailable " + e);						
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
		} catch (UnknownHostException e) {
			System.out.println("Error creating client. Unknown host in verifyConnection().");
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			System.out.println("Error creating client. IO Exception in verifyConnection().");
			e.printStackTrace();
			return false;
		}	
		return false;
	}
	
	/**
	 * Accept list of available filenames from Server
	 * Refreshes every 30 seconds
	 * 
	 * @param mediaList from GUI view.
	 */
	public void receiveListFromServer(ListView<String> finalMediaList) {
		
		//new Thread(new Runnable() {
			//@Override
			//public void run() {
		Runnable task = () -> {
			Platform.runLater(() -> {
				ObservableList<String> tempMediaList = FXCollections.observableArrayList();
				
				if(verifyConnection()) { 
			
					try {
						printWriter = new PrintWriter(new BufferedWriter(
								new OutputStreamWriter(socket.getOutputStream())));
						
						//send RPC
						System.out.println("Sending list request.");
						printWriter.print(RPC_REQUEST_LISTING);
						System.out.println("Sent.");

						if(printWriter.checkError()) {
							System.err.println("Client: Error writing to socket");
						}
						
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
								tempMediaList.add(buffer);						
							
						}
						
						//printWriter.print(RPC_REQUEST_SUCCESS);
						printWriter.flush();
						
						finalMediaList.setItems(tempMediaList);
						System.out.println("List retreived.");

					}catch(IOException e) {
						e.printStackTrace();
						System.out.println("Error receiving message from the client");
						closeEverything(socket, bufferedReader, bufferedWriter);
					}
				}
				else {
						System.out.println("Server unavailable");
						ObservableList<String> error = FXCollections.observableArrayList();
						error.add("Server Unavaiable.");
						finalMediaList.setItems(error);
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
//			bufferedWriter.write(RPC_REQUEST_FILE + " " + filename);
//			bufferedWriter.newLine(); //needed if server is using bufferedReader.readLine() to receive
//			bufferedWriter.flush();
		}catch(IOException e) {
			e.printStackTrace();
			System.out.println("Error sending message to the client.");
			closeEverything(socket, bufferedReader, bufferedWriter);
		}
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
				
				if(socket != null) {
					try {
						//initial read of file size
						bufferedReader = new BufferedReader(new InputStreamReader(
								socket.getInputStream()));
						size = Integer.parseInt(bufferedReader.readLine());
						System.out.println("File Size is: " + size);
						
						//second read for file data
						DataInputStream dis = new DataInputStream(socket.getInputStream());
						FileOutputStream fos = new FileOutputStream("src/cache/" + fileName);
							
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
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
}
