package player;

//import java.io.BufferedReader;
//import java.io.BufferedWriter;
//import java.io.DataInputStream;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.io.OutputStreamWriter;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import player.Controller;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.media.MediaPlayer;



public class Client {
	static final int RPC_REQUEST_SUCCESS = 0;	
	static final String RPC_REQUEST_LISTING = "requestlisting";
	static final String RPC_REQUEST_FILE = "requestfile";
	
	//socket parts
	private SSLSocketFactory socketfact;
	private SSLSocket socket;
	
	//IO readers/writers
	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;
	
	private PrintWriter printWriter;
	
	private String buffer;
//    private BufferedReader bufferedReader;
    
	DataInputStream dis;	

	/**
	 * Constructor for Client socket
	 * 
	 * DP: Needs Handshake and more for connection purposes
	 * @param socket
	 */
	public Client(SSLSocketFactory socketfact) {
//		System.setProperty("javax.net.ssl.trustStore","clientTrustStore");
//
//		System.setProperty("javax.net.ssl.trustStorePassword","changeit");
		
//		System.setProperty("javax.net.ssl.trustStore", "client.jks");
//		System.setProperty("javax.net.ssl.trustStorePassword", "client");
		try {
			//create sockets and perform handshake
			this.socketfact = socketfact;
			this.socket = (SSLSocket) socketfact.createSocket("localhost", 4433);
			socket.startHandshake();
			
			System.out.println("Socket handshake started");
			
			//create data movers
			this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error creating client.");
			closeEverything(socket, bufferedReader, bufferedWriter);
		}
	}
	
	public Boolean isConnected() {
		if(socket == null) {
			return false;
		}
		else
			return true;
	}
	
	/**
	 * Accept list of available filenames from Server\
	 * 
	 * @param Listview to contain items
	 */
	public void receiveListFromServer(ListView<String> finalMediaList) {
		System.out.println("Attempting to receive list from server");
		
			new Thread(new Runnable() {
				@Override
				public void run() {
					ObservableList<String> tempMediaList = FXCollections.observableArrayList();
					System.out.println("Trying to get into initial while loop...");
					if(socket != null) {
						System.out.println("Getting into first try");
						try {
							printWriter = new PrintWriter(new BufferedWriter(
									new OutputStreamWriter(
											socket.getOutputStream())));
							
							printWriter.print(RPC_REQUEST_LISTING);
							
							System.out.println("Sent RPC request");
							
							printWriter.flush();
							
							if(printWriter.checkError())
								System.err.println("Client: Error writing to socket");
							
							System.out.println("No errors found?");
							
//							bufferedReader = new BufferedReader(new InputStreamReader(
//									socket.getInputStream()));
							
							System.out.println("Reading server request");

//							System.out.println(buffer);
							System.out.println("We're here atm");
							buffer = "-1";
							System.out.println("Buffer is " + buffer);
//							buffer = bufferedReader.readLine();
							System.out.println("Uhm...!??");
							
							dis = new DataInputStream(socket.getInputStream());
							
							String testString = Integer.toString(RPC_REQUEST_SUCCESS);
							while(!buffer.equals(testString)) {
								try {
									buffer = bufferedReader.readLine();
									
								}catch(IOException e) {
									System.out.println(e);
								}
								buffer = buffer.replace("\0", "");
//								System.out.println("Right HERE");
//								buffer = bufferedReader.readLine();
//								System.out.println("Receiving something");
								System.out.println("Right HEEERE");
								System.out.println("Buffer is " + buffer);
								tempMediaList.add(buffer);
								System.out.println(tempMediaList);
								
								
								for(int i = 0; i < buffer.length(); i++) {
									char c = buffer.charAt(i);
									System.out.println(c + " = " + ((int)c));
								}
								
								for(int j = 0; j < testString.length(); j++) {
									char c = testString.charAt(j);
									System.out.println(c + " = " + ((int)c));
								}
								
								System.out.println("Does " + buffer + " = " + RPC_REQUEST_SUCCESS + " ? " + (buffer.equals(testString)));
//								System.out.println(buffer);								
							}
							
							printWriter.print(RPC_REQUEST_SUCCESS);

							
							// If our server said we had a successful request...
//							if(Integer.parseInt(buffer) == RPC_REQUEST_SUCCESS) {
//								buffer = "-1";
//								
//								System.out.println("We're here atm");
//								while(Integer.parseInt(buffer) != RPC_REQUEST_SUCCESS) {
//									System.out.println("Receiving something");
//									tempMediaList.add(buffer);
//									System.out.println(buffer);
//								}
//								System
							System.out.println("We're here now btw");
								System.out.println(tempMediaList);
								finalMediaList.setItems(tempMediaList);
//							}else {
//								System.out.println("Invalid server response: " + buffer);
//							}
							
//							String messageFromClient = bufferedReader.readLine();
							//Controller.addLabel(messageFromClient, vbox_messages);
						}catch(IOException e) {
							e.printStackTrace();
							System.out.println("Error receiving message from the client");
							closeEverything(socket, bufferedReader, bufferedWriter);
//							break;
						}
					}
					else {
							ObservableList<String> error = FXCollections.observableArrayList();
							error.add("Server Unavaiable.");
							finalMediaList.setItems(error);
						}
				}
				
				}).start();
	}
	
	/**
	 * Sends message to server via String to request a certain file from the list of 
	 * available media.
	 * 
	 * @param filename selected from media list
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
