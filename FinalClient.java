import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

class FinalClient 
{
	//Declare and initialise variables needed to be used across multiple methods
	static int portNum;
	//Set the filename to be read within a global variable, this allows for easy integration of future program development into user-decided files
	static String fileName = "SendingData.txt";
	static BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
	static InetAddress IPAddress = null;
	static DatagramSocket clientSocket = null;
	static DatagramPacket receivePacket;
	static byte[] sendDataByte;
	static byte[] receiveDataByte;
	static boolean resendNeeded = false;
	static String [] dataSegment = new String[4];
	static int retryAttempts;
	
	public static void main(String args[]) throws Exception
	{
		//Declare the variable used in the main method
		int windowSize;
		
		//Allow the user to specify the port and IP location of the server to connect to
		while (true)
		{
			//User can input IP to connect to
			System.out.println("\n\t\t\t\tWelcome\n\nEnter IP address or 'd' for default settings");
			String userInputIP = inFromUser.readLine();
			
			//User can input port number of server to connect to
			System.out.println("\nEnter a port number or 'd' for default settings");
			String userInputPort = inFromUser.readLine();
			
			//Try-catch block incase errors are thrown
			try
			{
				//If the user inputs 'd' use default IP settings - this is the local device IP
				if (userInputIP.equalsIgnoreCase("D"))
				{
					IPAddress = InetAddress.getLocalHost();
				}
				
				//Otherwise, set the user input to the IP to connect to
				else
				{
					IPAddress = InetAddress.getByName(userInputIP);
				}
				
				//If the user inputs 'd' use default port settings - this is the default server listening port
				if (userInputPort.equalsIgnoreCase("D"))
				{
					portNum= 56789;
				}
				
				//Otherwise, set the user input to the port to connect to
				else
				{
					portNum = Integer.parseInt(userInputPort);
				}
				
				//Send a connection message to the server, if this returns as failed, inform the user and retart loop
				if (!(sendData("Client Connected")))
				{
					System.out.println("Failed to send connection");
					continue;
				}
				//Indicate to the user if the client is currently waiting for a response
				System.out.println("\nWaiting for server response....");
				
				//Print the connection message sent by the server
				System.out.println(receiveData());
				
				//If a server timeout is detected, inform the user and restart the loop - incorrect settings may have been chosen
				if (resendNeeded)
				{
					System.out.println("Connection Timeout, no response from server");
					continue;
				}
				//Exit loop if this is successful, the server and client is now ready for data to be sent
				break;

			}
			//This can catch errors if the user input is unable to be translated into an IP and port number - restart loop
			catch(Exception e)
			{
				System.out.println("Oops, we had trouble with those settings, please try again with different ones");
				continue;
			}
		}	
		
		//Set the window size as 1 - This is also known as Stop-And-Wait
		windowSize = 1;
		
		//Start the dataController method with a window size of one
		dataController(windowSize);	
			
		//If the data has failed to be sent too many times, it indicates a large scale error, do not proceed, exit the program
		//Otherwise, proceed
		if (!(retryAttempts>2))
		{
			//Set the windowSize to 5 and call the dataController method again
			windowSize = 5;
			dataController(windowSize);
		}
		
		//Call the close() method, which closes the sockets and indicates to the server to close those sockets too
		close();
	}
	
	private static void dataController(int windowSizeIn) throws Exception
	{
		//Initialise the variables used within this method
		retryAttempts=0;
		String serverReply="";
		int travellingData=-1;
		int receivedAck=-1;
		char[] dataArray = null;
		
		//Variable ensures a lost packet is simulated only once per file
		int lostPacketSimulation=0;
		
		//Create an array list to keep track of the data sequence numbers currently inside the open window
		List<Integer> snWithinOpenWindow = new ArrayList<Integer>();
		
		//Indicate that a new file is being read with the current window size being used
		System.out.println("\n\t\t\t\tSending new data file");
		System.out.println("Sliding window size: " +windowSizeIn);
		
		
		
		//Call the readFile method, which reads the desired file in the same folder as the program and create a character array out of the data within the file
		dataArray = readFile(fileName).toCharArray();

		//Check for a unique unicode character which indicates the selected file not being able to be found
		if (dataArray[0] == (char)212f)
		{
			System.out.println("\nFile does not exist, exiting method");
			return;
		}
		
		//Print the data read from the desired file
		System.out.println("Data to be sent: \"" + String.valueOf(dataArray) + "\"");

		//Create a loop which populates the data segment as empty, this clears after any previous data files
		for (int i=0;i<dataSegment.length;i++)
		{
			dataSegment[i]=null;
		}
		
		//Create a loop which populates the open window with the correct amount of data to be sent
		for (int c=0;c<windowSizeIn;c++)
		{
			snWithinOpenWindow.add(c);
		}
			
		//Start the main loop which goes through each data character until all the data is sent from the file
		for (int i=0; i<dataArray.length;i++)
		{		
			//Introduce a program delay so that the interface is legible
			TimeUnit.SECONDS.sleep(2);
			
			//Try-catch block incase errors are thrown
			try
			{	
				//Create a loop which cycles through the characters already sent or within the open window
				for (int x=0; x<=(snWithinOpenWindow.get(snWithinOpenWindow.size()-1));x++)
				{
					//If a character has not yet been sent, but can fit within the open window size, send it
					if ((x > travellingData) && (snWithinOpenWindow.get(snWithinOpenWindow.indexOf(x))!=-1))
					{
						System.out.println("\nSending: \"" + dataArray[x] + "\" with sequence number \"" + x + "\"" );
						//Keep track of the data currently in transit to the server
						travellingData ++;	
						//Populate the segment with the sequence number to be sent to the server
						dataSegment[2] = String.valueOf(x);
						//Populate the segment with the data to be sent to the server
						dataSegment[3] = Character.toString(dataArray[x]);
						
						//If this is the first character being sent, populate the segment with the window size to be sent to the server
						if (x== 0)
						{
							dataSegment[0] = String.valueOf(windowSizeIn);
						}
						
						//If this is the last character being sent, populate the segment with an indicator for the server
						if (x== dataArray.length-1)
						{
							dataSegment[1] = (Character.toString((char)0017));
						}
						
						//Otherwise, clear this section of the data segment to avoid indicators being incorrectly sent
						else
						{
							dataSegment[1] = null;
						}
						
						//If the data is halfway through being sent, and a lost packet has not yet been simulated, simulate a lost packet
						if ((dataSegment[2].equals(String.valueOf(dataArray.length/2))) && (lostPacketSimulation==0))
						{
							System.out.println("\t\t\t\t*SIMULATED LOST PACKET*");
							lostPacketSimulation++;
						}
						
						//Otherwise, send convert the data segment into a string, seperated with a unique indicator and send it
						else if(!(sendData(String.join(Character.toString((char)19), dataSegment))))
						{
							//If the data failed to send, indicate that the data needs to be resent
							System.out.println("Data: \"" + dataArray[snWithinOpenWindow.get(x)] + "\"" + " failed to send");
							resendNeeded=true;			
						}
						
						//Otherwise indicate that the data does not need to be resent, this resets the value from previous data
						else
						{
							resendNeeded=false;
						}
					} 
				}
				
				//Initialise and reset variables needed to output the status of the open window
				String currentWindowSN="";
				String currentWindowData="";
				
				//Create a loop which cycles through the characters in the file in order to output the status of the open window
				for (int x=0; x<dataArray.length;x++)
				{
					//if the data has already been sent, add the data to two strings
					if (x < Collections.min(snWithinOpenWindow))
					{
						currentWindowSN+= " " + x;
						currentWindowData+= " " +dataArray[x];
					}
					//If the data is the first character within the open window, add brackets to indicate this
					if (x == Collections.min(snWithinOpenWindow))
					{
						currentWindowSN+= " [";
						currentWindowData+= " ["; 
					}
					//If the data is within the open window, add the data to the two strings
					if (snWithinOpenWindow.indexOf(x) != -1)
					{
						currentWindowSN+= " " + x;
						currentWindowData+= " " + dataArray[x];
					}
					//If the data is the last character within the open window, add brackets to indicate this
					if (x== Collections.max(snWithinOpenWindow))
					{
						currentWindowSN+= " ]";
						currentWindowData+= " ]";
					}
					//If the data has not yet been sent, add the data to thw two strings
					if ((x>Collections.max(snWithinOpenWindow)) && (x!=dataArray.length))
					{
						currentWindowSN+= " " + x;
						currentWindowData+= " " + dataArray[x];
					}
				}
				//Now print the two strings, to indicate to the user the status of the open window, in terms of data and sequence numbers
				System.out.println("\nCurrent window sequence Number:" + currentWindowSN);
				System.out.println("Current window data:           " + currentWindowData);
				
				
				//Introduce a program delay so that the interface is legible and to indicate that the client is waiting for a server response
				System.out.println("Waiting for server acknowledgement...");
				TimeUnit.SECONDS.sleep(2);
				
				//If the data does not need to be resent, wait for an acknowledgement from the server
				if (!(resendNeeded))
				{
					serverReply = receiveData();
				}
				//Convert the string received from the server, into an integer, catch an numberFormatExceptions incase the data cannot be transferred into an integer
				try
				{
					receivedAck = Integer.parseInt(serverReply);		
				}
				catch (NumberFormatException e)
				{
					System.out.println("Error with Ack received from server, trying to resend data");
					resendNeeded=true;
					serverReply="";
				}
			}
			//Catch errors
			catch (Exception e)
			{System.out.println("Error in main sending method");}		
			
			//If the data does not need to be resent and a valid ack has been received
			if ((!(resendNeeded)) && (snWithinOpenWindow.contains(receivedAck)))
			{
				System.out.println("\nAck " + receivedAck + " received");
				//If more data needs to be send, move the open window
				if (receivedAck<dataArray.length)
				{
					System.out.println("Moving window up");
				}
				
				//If an ack has been received and the open window cannot move due to no more data needing to be sent, remove the data from the opwn window
				if (receivedAck >= (dataArray.length-windowSizeIn))
				{
					snWithinOpenWindow.remove(snWithinOpenWindow.indexOf(receivedAck));
				}
				
				//Initialise/reset variables for loop
				int selectedLoopValue = 0;
				retryAttempts=0;
				
				//Create a loop to set the open window with correct data, if the open window has been moved up
				for (int x=0;x<snWithinOpenWindow.size();x++)
				{	
					//Increase the sequence numbers within the open window by one to move the window forward
					selectedLoopValue = snWithinOpenWindow.get(x) + 1;
					//If the final sequence number in the open window is not the final sequence number to be sent, add the data to the open window
					if ((snWithinOpenWindow.get(snWithinOpenWindow.size()-1) != dataArray.length-1))
					{
						snWithinOpenWindow.set(x, selectedLoopValue);
					}
				}
			}
			
			//Otherwise, if the data does not need to be resent and an invalid ack has been received, inform the user
			else if ((!(resendNeeded)) && (!(snWithinOpenWindow.contains(receivedAck))))
				{
					System.out.println("Openwindow: " + snWithinOpenWindow + " does not contain " + receivedAck);
				}
			
			//Otherwise, the data would need to be resent
			else
			{
				//Indicate to the user that there has been a timeout on the socket and data needs to be resent
				System.out.println("\n\t\t\t\tNo server response, timeout thrown! \n\t\t\t\tAttempting to resend data...");
				//Move the data to be sent back to the last sequence number that was not acknowledged
				travellingData=receivedAck;	
				//Clear the open window as it needs to be rewritten and moved back
				snWithinOpenWindow.clear();
				//Decrement i so that the main loop is extended 
				i--;
				
				//Create a loop which populates the open window with data to be resent, stop the loop if there is less data to be sent than the window size
				for (int c=1;(c<windowSizeIn+1) && (c+receivedAck<dataArray.length);c++)
				{
					snWithinOpenWindow.add(receivedAck+c);
				}
				
				//Increment the retry attempts
				retryAttempts++;
				//If theres been 3 retries, stop the program as a larger error is likely occurring
				if (retryAttempts>2)
				{
					System.out.println("Too many retries, program exiting");
					break;
				}
				
			}
		}
		//If the program ends as a success, indicate the completion to the user
		if (!(retryAttempts>2))
		{
			System.out.println("Successfully sent file");
		}
	}
	
	private static boolean sendData (String dataToBeSent)
	{
		//Try-catch block incase errors are thrown
		try
		{
			//If this is the first time data is being sent, initialise the DataByte buffer and client socket
			if (clientSocket==null)
			{
				clientSocket = new DatagramSocket();
				sendDataByte = new byte[1024];
			}
			
			//Prepare the data to be sent
			sendDataByte = dataToBeSent.getBytes();
			//Create data packet with data, data length, and destination
			DatagramPacket sendPacket = new DatagramPacket(sendDataByte, sendDataByte.length,IPAddress, portNum);
			clientSocket.send(sendPacket);
		}
		
		catch(Exception e)
		{
			//If error occurs, return a boolean so that error can be dealt with inside the main loop
			return false;
		}
		//If an error is not thrown, return a boolean so that the loop can confidently continue
		return true;
	}
	private static String receiveData() throws IOException
	{
		//initialise/reset the incoming data variable
		String incomingData = "";
		
		//Start the clientSocket timeout timer to 5 seconds
		clientSocket.setSoTimeout(5000);
		
		//Try-catch block incase errors or socket timeout exceptions are thrown
		try
		{
			//If this is the first data to be received, initialise the DataByte buffer and receivePacket
			if (receiveDataByte == null)
			{
				receiveDataByte = new byte[1024];
				receivePacket = new DatagramPacket(receiveDataByte,receiveDataByte.length);
			}
			
			//Receive the data
			clientSocket.receive(receivePacket);
			//Store the incoming data into a variable
			incomingData = new String(receivePacket.getData(), 0, receivePacket.getLength());
			//The data does not need to be resent as the acknowledgement has been received
			resendNeeded=false;
		}
		catch (SocketTimeoutException e)
		{
			//if a SocketTimeoutExceotion occurs, there has not been a reply from the server, set the data to be resent
			resendNeeded = true;
		}
		catch (Exception e)
		{
			//Return a string to indicate a different error has occurred
			return "Error occurred receiving data";
		}
		//Return the received data back to the main loop
		return incomingData;
	}
	private static String readFile (String fileName)
	{
		//Try-catch block as errors can be thrown with reading files
		try 
		{
			//Initialise a variable to store the read data
			String data = "";
			//Create a new file variable with the name of the desired filename
			File sendDataFile = new File (fileName);
			//Initialise a scanner to read the file
			Scanner reader = new Scanner(sendDataFile);
			
			//Create a loop to read the data if there is more data to be read within the file, this allows for larger data files to be read
			while (reader.hasNextLine())
			{
				//If the data being read is not blank, add it to the string
				if (!(data.equals("")))
						{
							data += "\n";
						}
				data += reader.nextLine();
			}
			
			//Close the scanner when it is done being used
			reader.close();
			//compile the data read into legible words
			data.replace(" ", "");
			//Return this read data to the main loop
			return data;

		}
		//Catch FileNotFoundException if the file needing to be read does not exist
		catch (FileNotFoundException e)
		{
			System.out.println("Error Occurred");
			//Return a unique unicode character to indicate the selected file not being able to be found
			return Character.toString((char)212f);
		}

	}
	private static void close()
	{
		//Create a loop which populates the data segment with 'null', if the index is one, add the unique identifier to indicate the closure of the server socket
		for (int i=0;i<dataSegment.length;i++)
		{
			if (i==1)
			{
				dataSegment[i]= Character.toString((char)0004);
			}
			else
			{
				dataSegment[i]=null;
			}
		}
		//Convert this data segment into a string and send this to the server, indicating to close the server socket as it is no longer needed
		sendData(String.join(Character.toString((char)19), dataSegment));
		System.out.println("\n\nClosure of client and server initiated\nGoodbye");
		//Close the client socket as it is no longer needed
		clientSocket.close();
	}
}