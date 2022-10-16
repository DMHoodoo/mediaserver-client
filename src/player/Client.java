package player;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;

/**
 * Class to create and use an SSL client connection
 * 
 * @author Dacia Pennington
 *
 */
public class Client {
	// RPC finals for the server
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
	static final int MALFORMED_REQUEST = 3;
	
	// socket parts
	private SSLSocketFactory socketfact;
	private SSLSocket socket;

	// IO readers/writers
	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;
	private PrintWriter printWriter;

	private String buffer;
	private final int PORT_A = 4433;
	private final int PORT_B = 4434;
	private final String CACHE = "cache/";

	private boolean checksumMatch = true;
	private boolean isConnected = false;

	// Semaphore to ensure list retrieval/file downloads/checksum matching etc.
	// don't overlap
	private Semaphore mutex;

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
			// create sockets and perform handshake

			try {

				this.socketfact = socketfact;
				this.socket = (SSLSocket) socketfact.createSocket("localhost", PORT_A);
				socket.startHandshake();

				// create data movers
				this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

				mutex = new Semaphore(1);
			} catch (ConnectException e) {
				System.out.println("Server is unavailable.");
			}

		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error creating client.");
			closeEverything(socket, bufferedReader, bufferedWriter);
		}

	}

	/**
	 * Checks server response to validate connection is active
	 * 
	 * @param bufferedReader
	 * @return null if an error was encountered, otherwise what was returned from
	 *         the socket read
	 */
	public String processResponse(BufferedReader bufferedReader) {
		String serverReply = null;

		try {
			serverReply = bufferedReader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}

		String[] splitString = serverReply.split(" ");

		if (splitString.length == 2) {
			int mainCode = Integer.parseInt(splitString[0].replace("\0", ""));
			int subCode = Integer.parseInt(splitString[1].replace("\0", ""));

			switch (mainCode) {
			case RPC_REQUEST_SUCCESS:
				return serverReply;
			case SERVER_ERROR:
				System.out.println("Server failure! Error Code: " + subCode);
				return null;
			case RPC_ERROR:
				switch (subCode) {
				case INVALID_COMMAND:
					System.out.println("Invalid command issued.");
					return null;
				case TOO_FEW_ARGS:
					System.out.println("Too few arguments supplied to command");
					return null;
				case TOO_MANY_ARGS:
					System.out.println("Too many arguments supplied to command");
					return null;
				case MALFORMED_REQUEST:
					System.out.println("Malformed request");
					return null;
				default:
					System.out.println("Undefined error");
					return null;					
				}
			default:
				return serverReply;
			}
		} else 
			return serverReply;
		
	}

	/**
	 * Checks if client is connected. If false new connection is attempted several
	 * times.
	 * 
	 * @return
	 */
	public void verifyConnection(CountDownLatch threadSignal) {

		new Thread(new Runnable() {
			public void run() {
				boolean connectionConfirmed = false;
				try {
					printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

					bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

					// Send request to see if connection is still active
					printWriter.print(RPC_REQUEST_ISALIVE + "\0");

					printWriter.flush();

					// Look for response, if "pong", then connected.
					buffer = processResponse(bufferedReader);

					if (buffer == null) {
						System.out.println("Client connection has failed. Attempting to re-connect.");
					} else {
						isConnected = true;
						connectionConfirmed = true;
						threadSignal.countDown();
					}

				} catch (IOException e1) {
					e1.printStackTrace();
				} catch (NullPointerException e) {
					System.out.println("Encountered a null socket.");
				}

				if (!connectionConfirmed) {
					isConnected = false;
					Object obj = new Object();

					// if not connected, attempt both ports x5
					try {
						synchronized (obj) {

							// try port A
							for (int i = 1; i < 6 && !connectionConfirmed; i++) {

								try {
									System.out.println("Retrying DEFAULT port");
									socket = (SSLSocket) socketfact.createSocket("localhost", PORT_A);
									socket.startHandshake();
									// create data movers
									bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
									bufferedWriter = new BufferedWriter(
											new OutputStreamWriter(socket.getOutputStream()));

									isConnected = true;
									connectionConfirmed = true;
									System.out.println("Connection re-established!");
									threadSignal.countDown();
								} catch (SocketException e) {
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

							}

							// try port B
							if (!connectionConfirmed) {
								for (int i = 1; i < 6 && !connectionConfirmed; ++i) {
									try {
										System.out.println("Retrying BACKUP port");										
										socket = (SSLSocket) socketfact.createSocket("localhost", PORT_B);
										socket.startHandshake();

										// create data movers
										bufferedReader = new BufferedReader(
												new InputStreamReader(socket.getInputStream()));
										bufferedWriter = new BufferedWriter(
												new OutputStreamWriter(socket.getOutputStream()));

										isConnected = true;
										connectionConfirmed = true;
										System.out.println("Connection re-established!");
										threadSignal.countDown();
									} catch (SocketException e) {
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
								}
							}
						}
					} catch (UnknownHostException e) {
						System.out.println("Error creating client. Unknown host in verifyConnection().");
						threadSignal.countDown();
						e.printStackTrace();
						isConnected = false;
					} catch (IOException e) {
						System.out.println("Error creating client. IO Exception in verifyConnection().");
						threadSignal.countDown();
						e.printStackTrace();
						isConnected = false;
					}
				}
			}
		}).start();
	}

	/**
	 * Accept list of available filenames from Server Refreshes every 30 seconds
	 * 
	 * @param mediaList from GUI view.
	 */
	public ListView<String> receiveListFromServer(ListView<String> finalMediaList) {

		try {
			mutex.acquire();
		} catch (InterruptedException e2) {
			System.out.println(e2);
		}

		ObservableList<String> tempMediaList = FXCollections.observableArrayList();
		HashSet<String> allFiles = new HashSet<>();
		File[] cacheFiles = new File(CACHE).listFiles();

		CountDownLatch verifyThreadSignal = new CountDownLatch(1);

		verifyConnection(verifyThreadSignal);

		try {
			verifyThreadSignal.await();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		if (this.isConnected) {

			try {
				printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

				// send RPC
				System.out.println("Sending list request.");
				printWriter.print(RPC_REQUEST_LISTING);

				if (printWriter.checkError()) {
					System.err.println("Client: Error writing to socket");
				}

				buffer = processResponse(bufferedReader);

				if (buffer == null) {
					mutex.release();
					return null;
				}

				buffer = "-1";

				String RPC_SUCCESS_STRING = Integer.toString(RPC_REQUEST_SUCCESS);
				while (!buffer.equals(RPC_SUCCESS_STRING)) {

					try {
						buffer = bufferedReader.readLine();
					} catch (IOException e) {
						System.out.println(e);
					}

					buffer = buffer.replace("\0", "");

					if (!buffer.equals(RPC_SUCCESS_STRING)
							&& !buffer.equals(RPC_SUCCESS_STRING + " " + RPC_SUCCESS_STRING))
						allFiles.add(buffer);
				}

				printWriter.flush();
				
				mutex.release();

				/**
				 * Add items in cache that may have been removed from server and remove any
				 * duplicates.
				 */
				for (File file : cacheFiles) {
					if (file.isFile()) {
						CountDownLatch threadSignal = new CountDownLatch(1);
						
						validateFileToServer(file.getName(), threadSignal);

						try {
							threadSignal.await();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						CountDownLatch threadSignal2 = new CountDownLatch(1);
						if (!checksumMatch) {
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
				
				for (String filename : allFiles) 
					tempMediaList.add(filename);
				

				ListView<String> listToReturn = new ListView<String>();
				listToReturn.setItems(tempMediaList);

				return listToReturn;

			} catch (IOException e) {
				mutex.release();
				e.printStackTrace();
				System.out.println("Error receiving message from the client");
				closeEverything(socket, bufferedReader, bufferedWriter);
			}
		}
		
		mutex.release();
		return finalMediaList;
	}

	/**
	 * Method ensures that the client has the most up to date version of the file on
	 * the server via md5 checksum.
	 * 
	 * @param fileName
	 * @param threadSignal
	 */
	public void validateFileToServer(String fileName, CountDownLatch threadSignal) {

		new Thread(new Runnable() {

			@Override
			public void run() {

				try {
					PrintWriter printWriter = new PrintWriter(
							new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

					printWriter.print(RPC_REQUEST_MD5 + " \"" + fileName + "\"");

					String buffer;

					buffer = processResponse(bufferedReader);

					if (buffer == null) {
						threadSignal.countDown();
						return;
					}

					MessageDigest md = MessageDigest.getInstance("MD5");

					File file = new File("cache/" + fileName);
					String checksum = checksum(md, file);

					printWriter.flush();

					checksumMatch = (checksum.equals(buffer));

					threadSignal.countDown();

				} catch (IOException e) {
					System.out.println("IOException encountered " + e);
				} catch (NoSuchAlgorithmException e) {
					System.out.println("NoSuchAlgorithm Exception: " + e);
				}
			}
		}).start();

	}

	private String checksum(MessageDigest digest, File file) {
		FileInputStream fileInputStream = null;

		try {
			fileInputStream = new FileInputStream(file);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		byte[] byteArray = new byte[1024];
		int bytesRead = 0;

		try {
			while ((bytesRead = fileInputStream.read(byteArray)) != -1) 
				digest.update(byteArray, 0, bytesRead);
			
			fileInputStream.close();

			byte[] bytes = digest.digest();

			String finalMD5String = "";

			// Convert the MD5 digest to a string for comparison
			for (int i = 0; i < bytes.length; i++)
				finalMD5String += String.format("%02X", bytes[i]).toLowerCase();

			return finalMD5String;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Receive media file from server
	 * 
	 * @param fileName of sought after media
	 */
	public Media receiveMediaFromServer(String fileName, CountDownLatch threadSignal) {

		try {
			mutex.acquire();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}		

		int size;
		byte[] buffer;
		int read;
		int remaining = 0;

		CountDownLatch verifyThreadSignal = new CountDownLatch(1);

		verifyConnection(verifyThreadSignal);

		try {
			verifyThreadSignal.await();
		} catch (InterruptedException e1) {
			threadSignal.countDown();
		}

		if (isConnected) {
			try {
				printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

				printWriter.print(RPC_REQUEST_FILE + " \"" + fileName + "\"");


				if (printWriter.checkError())
					System.err.println("Client: Error writing to socket");

				printWriter.flush();

				// initial read of file size
				bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				String strBuffer = processResponse(bufferedReader);

				if (strBuffer == null) {
					threadSignal.countDown();
					mutex.release();
					return null;
				}
				
				size = Integer.parseInt(strBuffer);				

				// second read for file data
				DataInputStream dis = new DataInputStream(socket.getInputStream());
				FileOutputStream fos = new FileOutputStream("cache/" + fileName);

				buffer = new byte[256];

				remaining = size;

				// receive file and store in cache
				while ((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
					remaining -= read;
					fos.write(buffer, 0, read);
				}
				
				fos.close();
								
				bufferedReader.readLine();

				mutex.release();
				try {
					return new Media(new File(CACHE + fileName).toURI().toString());
				} catch (MediaException e) {
					System.out.println("Media unsupported, skipping.");
					return null;
				}
			} catch (IOException e) {
				System.out.println("Error making input file streams.");
				e.printStackTrace();
			}
		} else {
			System.out.println("Releasing download mutex");
			mutex.release();
			threadSignal.countDown();
		}

		mutex.release();
		return null;
	}

	/**
	 * Shuts down all readers and sockets upon failure
	 * 
	 * @param socket
	 * @param bufferedReader
	 * @param bufferedWriter
	 */
	public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
		try {
			if (bufferedReader != null) {
				bufferedReader.close();
			}
			if (bufferedWriter != null) {
				bufferedWriter.close();
			}
			if (socket != null) {
				socket.close();
			}
			if (this.bufferedReader != null) {
				this.bufferedReader.close();
			}
			if (this.bufferedWriter != null) {
				this.bufferedWriter.close();
			}
			if (this.socket != null) {
				this.socket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void breakupWithServer() {
		CountDownLatch verifyThreadSignal = new CountDownLatch(1);

		verifyConnection(verifyThreadSignal);

		try {
			printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

			printWriter.print(RPC_DISCONNECT);
		} catch (IOException e) {
			System.out.println("Error breaking up with Server.");
		}
	}

}
