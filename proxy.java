/**
 * @(#)proxy.java
 * @author Sean Tan
 */

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.*;
import java.lang.*;

/** 
 * This class implements a multithreaded simple minded HTTP server. This 
 * server doesn't handle CGI. All it does it listens on port 8080 and 
 * waits for connections and servers requested documents.
 */
public class proxy {
	// The port number on which the server will be listening on
	private static int HTTP_PORT;
	// Set of websites that are not allowed
	private Set<String> excludedWebsites = new HashSet<String>();
	// Name of Log file
	private static String logFileName;

	// constructor.
	public proxy() {
	}

	public ServerSocket getServer() throws Exception {
		return new ServerSocket(HTTP_PORT);
	}

	// multi-threading -- create a new connection for each request
	public void run() {
		try {
			Scanner sc = new Scanner(new File("excluded.txt"));
			while (sc.hasNextLine()) {
				String next = sc.nextLine();
				try {
					excludedWebsites.add(InetAddress.getByName(next).getHostAddress());
				} catch (UnknownHostException e) {
					System.out.println("Host address for " + next + "cannot be found");
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		ServerSocket listen;
		try {
			listen = getServer();
			while(true) {
				Socket client = listen.accept();
				Connects cc = new Connects(client, excludedWebsites, logFileName);
			}
		} catch(Exception e) {
			System.out.println("Exception..."+e);
		}
	}

	// main program
	public static void main(String argv[]) throws Exception {
		//System.setSecurityManager(new OurHttpdSecurityManager());
		// Stop program if less than 2 arguments
		if (argv.length < 2) {
			System.out.println("Missing arguments!");
			return;
		}
		HTTP_PORT = Integer.parseInt(argv[0]);
		logFileName = argv[1];

		proxy ht = new proxy();
		ht.run();
	}
}

/**
 * Runs thread to handle each request
 */
class Connects extends Thread {
	Socket client;
	BufferedReader is;
	PrintWriter os;
	Set<String> excludedWebsites;
	String fileName;

	public Connects(Socket s, Set<String> excludedWebsites, String fileName) { // constructor
		this.fileName = fileName;
		client = s;
		this.excludedWebsites = excludedWebsites;
		try {
			is = new BufferedReader(new InputStreamReader(client.getInputStream()));
			os = new PrintWriter(client.getOutputStream(), true);
		} catch (IOException e) {
			try {
				client.close();
			} catch (IOException ex) {
				System.out.println("Error while getting socket streams.."+ex);
			}
			return;
		}
		this.start(); // Thread starts here...this start() will call run()
	}

	/**
	 * Sends request to server
	 * @param addressString IP address that the client is connecting to
	 * @param host host name of the site that the client is connecting to
	 * @param output stream that sends the request lines to the server that the client is connecting to
	 */
	private void sendRequestToServer(String addressString, String host, DataOutputStream toServer) {
		try {
			toServer.writeBytes("GET " + addressString + " HTTP/1.1\r\n");
			toServer.writeBytes("Host: " + host + "\r\n");
			toServer.writeBytes("\r\n");
			toServer.flush();
		}
		catch (Exception e) {
			System.out.println("Unable to send request...");
			e.printStackTrace();
		}
	}
	
	/**
	 * receives the response from the server and sends it to the client's browser
	 * @param fromServer reads in the response from the server
	 */
	private void getResponseFromServer(BufferedReader fromServer) {
		try {
			String response;
			while ((response = fromServer.readLine()) != null) {
				os.println(response);
				os.flush();
			}
			os.println("\r\n");
			os.flush();
		} catch (Exception e) {
			System.out.println("Could not write to client...");
			e.printStackTrace();
		}
	}
	
	/**
	 * checks if the IP address that the user is attempting to connect to is in the restricted list
	 * @param ipAddress the IP address to check
	 * @param status line but missing the last column with \<Allowed\> and \<NotAllowed\>
	 * @param fileWriter writes to the log file specified by user when starting the proxy
	 * @return true if the ipAddress is allowed to be accessed else false
	 */
	private boolean checkAllowed(String ipAddress, String status, BufferedWriter fileWriter) {
		boolean result = true;
		try {
			if (excludedWebsites.contains(ipAddress)) {
				status += " <NotAllowed>";
				System.out.println(status);
				fileWriter.write(status);
				result = false;
			}
			else {
				status += " <Allowed>";
				System.out.println(status);
				fileWriter.write(status);
			}
			fileWriter.newLine();
			fileWriter.close();
		} catch (IOException e) {
			System.out.println("Error with log file...");
			e.printStackTrace();
		}
		return result;
	}
	
	/**
	 * runs the thread
	 */
	public void run() {
		try {
			// get a request and parse it.

			File log = new File(fileName);

			if (!log.exists()) {
				log.createNewFile();
			}

			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(log.getAbsoluteFile(), true));

			String addressString = is.readLine().split(" ")[1];
			String host = is.readLine().split(" ")[1];
			try {
				InetAddress address = InetAddress.getByName(host);
				String ipAddress = address.getHostAddress();
				String status = host + " " + ipAddress + " " + LocalDateTime.now();
				
				if (!checkAllowed(ipAddress, status, fileWriter)) {
					ipAddress = InetAddress.getByName("www.anti-smoking.org").getHostAddress();
					host = "www.anti-smoking.org";
				}
				
				Socket server = new Socket(ipAddress, 80);
				DataOutputStream toServer = new DataOutputStream(server.getOutputStream());
				BufferedReader fromServer = new BufferedReader(new InputStreamReader(server.getInputStream()));

				sendRequestToServer(addressString, host, toServer);
				getResponseFromServer(fromServer);
				
				try {
					toServer.close();
					os.close();
				} catch (Exception e) {
					System.out.println("Could not close streams...");
					e.printStackTrace();
				}
				if (client != null) client.close();
				if (server != null) server.close();
			} catch (UnknownHostException e) {
				System.out.println("Unknown host");
			}
		} catch ( IOException e ) {
			System.out.println( "I/O error " + e );
		} catch (Exception ex) {
			System.out.println("Exception: "+ex);
		}
	}
}
