package player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import player.Controller;
import javafx.scene.media.MediaPlayer;
 
public class Client {
	
	//socket parts
	private SSLSocketFactory socketfact;
	private SSLSocket socket;
	
	//IO readers/writers
	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;
	DataInputStream dis;	

	/**
	 * Constructor for Client socket
	 * 
	 * DP: Needs Handshake and more for connection purposes
	 * @param socket
	 */
	public Client(SSLSocketFactory socketfact) {
		try {
			//create sockets and perform handshake
			this.socketfact = socketfact;
			this.socket = (SSLSocket) socketfact.createSocket("localhost", 4433);
			socket.startHandshake();
			
			//create data movers
			this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error creating client.");
			closeEverything(socket, bufferedReader, bufferedWriter);
		}
	}
	
	/**
	 * Accept list of available filenames from Server\
	 * 
	 * 
	 * DP:method needed in controller to link a scroll list to this received list
	 * DP: assume method needs to be made ArrayList to return list.
	 * 
	 */
	public void receiveListFromServer() {
		//needs to run on separate thread so server is not blocked 
		//constantly waiting for messages
		//DP: This is for single line text only. Needs to import ArrayList
			new Thread(new Runnable() {

				@Override
				public void run() {
					while(socket.isConnected()) {
						try {
							String messageFromClient = bufferedReader.readLine();
							//Controller.addLabel(messageFromClient, vbox_messages);
						}catch(IOException e) {
							e.printStackTrace();
							System.out.println("Error receiving message from the client");
							closeEverything(socket, bufferedReader, bufferedWriter);
							break;
						}
					}
					
				}
				
				}).start();
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
			bufferedWriter.write(filename);
			bufferedWriter.newLine(); //needed if server is using bufferedReader.readLine() to receive
			bufferedWriter.flush();
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
	 */
	public void receiveMediaFromServer(MediaPlayer player) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while(socket.isConnected()) {
					try {
						String messageFromClient = bufferedReader.readLine();
						//Controller.addLabel(messageFromClient, player);
					}catch(IOException e) {
						e.printStackTrace();
						System.out.println("Error receiving message from the client");
						closeEverything(socket, bufferedReader, bufferedWriter);
						break;
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
